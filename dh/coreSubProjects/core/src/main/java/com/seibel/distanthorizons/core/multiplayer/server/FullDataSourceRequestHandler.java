package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.level.AbstractDhServerLevel;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.exceptions.SectionRequiresSplittingException;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FullDataSourceRequestHandler implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logNetworkEventToFile)
			.build();
	
	
	private final AbstractDhServerLevel serverLevel;
	private final ThreadPoolExecutor tickerThread;
	
	private String getLevelIdentifier() { return this.serverLevel.getLevelWrapper().getDhIdentifier(); }
	private GeneratedFullDataSourceProvider fullDataSourceProvider() { return this.serverLevel.serverside.fullDataFileHandler; }
	private List<BeaconBeamDTO> getAllBeamsForPos(long pos) { return this.serverLevel.beaconBeamRepo.getAllBeamsForPos(pos); }
	
	private final ConcurrentMap<Long, DataSourceRequestGroup> requestGroupsByPos = new ConcurrentHashMap<>();
	private final ConcurrentMap<Long, DataSourceRequestGroup> requestGroupsByFutureId = new ConcurrentHashMap<>();
	private final ConcurrentMap<Long, SyncRequestState> syncRequestsByFutureId = new ConcurrentHashMap<>();
	private final AtomicBoolean closed = new AtomicBoolean();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceRequestHandler(AbstractDhServerLevel serverLevel)
	{
		this.serverLevel = serverLevel;
		
		String levelId = this.serverLevel.getServerLevelWrapper().getDhIdentifier();
		this.tickerThread = ThreadUtil.makeSingleDaemonThreadPool("DataSource Request Ticker ["+levelId+"]");
		this.tickerThread.execute(this::tickLoop);
	}
	
	
	
	//==================//
	// network handling //
	//==================//
	
	public void queueLodSyncForRequestMessage(ServerPlayerState serverPlayerState, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet rateLimiterSet)
	{
		if (this.closed.get() || serverPlayerState.isClosing())
		{
			return;
		}

		if (!serverPlayerState.sessionConfig.getSynchronizeOnLoad())
		{
			message.sendResponse(new RequestRejectedException("Operation is disabled in config."));
			return;
		}
		
		if (!rateLimiterSet.syncOnLoginRateLimiter.tryAcquire(message))
		{
			return;
		}
		
		SyncRequestState syncRequestState = new SyncRequestState(serverPlayerState, message.futureId, rateLimiterSet);
		this.syncRequestsByFutureId.put(message.futureId, syncRequestState);
		if (this.closed.get() || serverPlayerState.isClosing())
		{
			syncRequestState.cancel();
			return;
		}
		
		AbstractExecutorService fileHandlerExecutor = ThreadPoolUtil.getFileHandlerExecutor();
		if (fileHandlerExecutor == null)
		{
			LOGGER.warn("Unable to send FullDataSourceResponseMessage - getFileHandlerExecutor() is null");
			syncRequestState.finish();
			return;
		}
		
		AbstractExecutorService networkCompressionExecutor = ThreadPoolUtil.getNetworkCompressionExecutor();
		if (networkCompressionExecutor == null)
		{
			LOGGER.warn("Unable to send FullDataSourceResponseMessage - getNetworkCompressionExecutor() is null");
			syncRequestState.finish();
			return;
		}
		
		CompletableFuture<FullDataSourceV2> getServerDatasourceFuture;
		try
		{
			getServerDatasourceFuture = CompletableFuture.supplyAsync(() ->
			{
				if (syncRequestState.isCancelled() || this.closed.get())
				{
					return null;
				}

				try
				{
					// the client timestamp will be null if we want to retrieve the LOD regardless of when it was last updated
					long clientTimestamp = (message.clientTimestamp != null) ? message.clientTimestamp : -1;
					
					// the server timestamp will be null if no LOD data exists for this position
					Long serverTimestamp = this.fullDataSourceProvider().getTimestampForPos(message.sectionPos);
					if (serverTimestamp == null || serverTimestamp <= clientTimestamp)
					{
						return null;
					}
					
					return this.fullDataSourceProvider().get(message.sectionPos);
				}
				catch (Exception e)
				{
					LOGGER.error("Unexpected issue getting server-side LOD for request at pos [" + DhSectionPos.toString(message.sectionPos) + "], error: [" + e.getMessage() + "].", e);
					return null;
				}
			}, fileHandlerExecutor);
		}
		catch (RejectedExecutionException e)
		{
			syncRequestState.finish();
			return;
		}
		
		getServerDatasourceFuture.whenComplete((fullDataSource, throwable) ->
		{
			if (syncRequestState.isCancelled() || this.closed.get() || serverPlayerState.isClosing())
			{
				if (fullDataSource != null)
				{
					fullDataSource.close();
				}
				syncRequestState.finish();
				return;
			}

			if (throwable != null)
			{
				LOGGER.debug("LOD sync request ended before a response could be sent: [" + throwable.getClass().getSimpleName() + "].");
				syncRequestState.finish();
				return;
			}

			try
			{
				CompletableFuture.runAsync(() ->
				{
					if (syncRequestState.isCancelled() || this.closed.get() || serverPlayerState.isClosing())
					{
						if (fullDataSource != null)
						{
							fullDataSource.close();
						}
						syncRequestState.finish();
						return;
					}
					
					if (fullDataSource == null)
					{
						message.sendResponse(new FullDataSourceResponseMessage(null));
						syncRequestState.finish();
						return;
					}

					try
					{
						FullDataPayload payload;
						try
						{
							payload = new FullDataPayload(fullDataSource, this.getAllBeamsForPos(message.sectionPos));
						}
						finally
						{
							fullDataSource.close();
						}

						serverPlayerState.fullDataPayloadSender.sendInChunks(payload, () ->
						{
							if (!serverPlayerState.isClosing())
							{
								message.sendResponse(new FullDataSourceResponseMessage(payload));
							}
							syncRequestState.finish();
						}, syncRequestState::finish);
					}
					catch (Exception e)
					{
						LOGGER.error("Unexpected issue sending request for pos [" + DhSectionPos.toString(message.sectionPos) + "], error: [" + e.getMessage() + "].", e);
						syncRequestState.finish();
					}
				}, networkCompressionExecutor).exceptionally(responseThrowable ->
				{
					syncRequestState.finish();
					return null;
				});
			}
			catch (RejectedExecutionException e)
			{
				if (fullDataSource != null)
				{
					fullDataSource.close();
				}
				syncRequestState.finish();
			}
		});
		
	}
	
	public void queueWorldGenForRequestMessage(ServerPlayerState serverPlayerState, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet rateLimiterSet)
	{
		if (this.closed.get() || serverPlayerState.isClosing())
		{
			return;
		}

		if (!Config.Common.WorldGenerator.generatorPlan.get().generationEnabled)
		{
			message.sendResponse(new RequestRejectedException("Operation is disabled in config."));
			return;
		}
		
		if (!rateLimiterSet.generationRequestRateLimiter.tryAcquire(message))
		{
			return;
		}
		
		this.doQueueWorldGenForRequestMessage(new DataSourceRequestGroup.RequestData(serverPlayerState, message, rateLimiterSet));
	}
	
	private void doQueueWorldGenForRequestMessage(DataSourceRequestGroup.RequestData requestData)
	{
		if (this.closed.get() || requestData.serverPlayerState.isClosing())
		{
			requestData.releaseRateLimitOnce();
			return;
		}

		while (!this.closed.get())
		{
			AtomicBoolean createdNewGroup = new AtomicBoolean(false);
			DataSourceRequestGroup requestGroup = this.requestGroupsByPos.computeIfAbsent(requestData.sectionPos(), pos ->
			{
				DataSourceRequestGroup newGroup = new DataSourceRequestGroup(pos);
				try
				{
					newGroup.tryAddRequest(requestData);
					createdNewGroup.set(true);
					
					this.tryFulfillDataSourceRequestGroup(newGroup, pos);
					
					LOGGER.debug("[" + this.getLevelIdentifier() + "] Created request group for pos [" + DhSectionPos.toString(pos) + "].");
					return newGroup;
				}
				catch (Exception e)
				{
					LOGGER.error("Unable to queue request for pos: ["+DhSectionPos.toString(requestData.sectionPos())+"], error: ["+e.getMessage()+"].", e);
				}
				
				return newGroup;
			});
			
			// If this fails, loop until either a permit is acquired or the group is removed to create another one
			if (!createdNewGroup.get() && !requestGroup.tryAddRequest(requestData))
			{
				Thread.yield();
				continue;
			}
			
			this.requestGroupsByFutureId.put(requestData.futureId(), requestGroup);
			if (this.closed.get() || requestData.serverPlayerState.isClosing())
			{
				this.cancelGenerationRequest(requestData.futureId());
			}
			return;
		}

		requestData.releaseRateLimitOnce();
	}
	
	public void cancelRequest(long requestId)
	{
		SyncRequestState syncRequestState = this.syncRequestsByFutureId.get(requestId);
		if (syncRequestState != null)
		{
			syncRequestState.cancel();
			return;
		}

		this.cancelGenerationRequest(requestId);
	}

	private boolean cancelGenerationRequest(long requestId)
	{
		DataSourceRequestGroup requestGroup = this.requestGroupsByFutureId.remove(requestId);
		if (requestGroup == null)
		{
			return false;
		}
		
		DataSourceRequestGroup.RequestData removedRequest = requestGroup.tryRemoveRequest(requestId, requestsToTransfer ->
		{
			LOGGER.debug("[" + this.getLevelIdentifier() + "] Cancelled request group [" + DhSectionPos.toString(requestGroup.pos) + "].");
			this.requestGroupsByPos.remove(requestGroup.pos, requestGroup);
			
			FullDataSourceV2 completedDataSource = requestGroup.takeFullDataSource();
			if (completedDataSource != null)
			{
				completedDataSource.close();
			}

			if (!requestsToTransfer.isEmpty() && !this.closed.get())
			{
				for (DataSourceRequestGroup.RequestData requestToTransfer : requestsToTransfer)
				{
					this.doQueueWorldGenForRequestMessage(requestToTransfer);
				}
			}
			else
			{
				this.fullDataSourceProvider().removeRetrievalRequestIf(pos -> pos == requestGroup.pos);
			}
		});
		
		if (removedRequest != null)
		{
			removedRequest.releaseRateLimitOnce();
			return true;
		}

		return false;
	}

	public void cancelRequestsForPlayer(ServerPlayerState serverPlayerState)
	{
		int cancelledSyncRequestCount = 0;
		for (SyncRequestState syncRequestState : this.syncRequestsByFutureId.values())
		{
			if (syncRequestState.serverPlayerState == serverPlayerState)
			{
				syncRequestState.cancel();
				cancelledSyncRequestCount++;
			}
		}

		int cancelledGenerationRequestCount = 0;
		for (Map.Entry<Long, DataSourceRequestGroup> entry : this.requestGroupsByFutureId.entrySet())
		{
			DataSourceRequestGroup.RequestData requestData = entry.getValue().requestMessages.get(entry.getKey());
			if (requestData != null && requestData.serverPlayerState == serverPlayerState)
			{
				if (this.cancelGenerationRequest(entry.getKey()))
				{
					cancelledGenerationRequestCount++;
				}
			}
		}

		if (cancelledSyncRequestCount != 0 || cancelledGenerationRequestCount != 0)
		{
			LOGGER.info("[" + this.getLevelIdentifier() + "] Player left; cancelled [" + cancelledGenerationRequestCount + "] generation requests and [" + cancelledSyncRequestCount + "] LOD sync requests. Remaining generation groups: [" + this.requestGroupsByPos.size() + "].");
		}
	}
	
	private void tryFulfillDataSourceRequestGroup(DataSourceRequestGroup requestGroup, long pos)
	{
		final GeneratedFullDataSourceProvider provider = this.fullDataSourceProvider();
		
		provider.getAsync(pos)
			.thenAccept((FullDataSourceV2 fullDataSource) ->
		{
			if (requestGroup.isClosed.get() || this.closed.get())
			{
				fullDataSource.close();
				return;
			}

			if (provider.generationStepsAreFullyGenerated(fullDataSource.columnGenerationSteps))
			{
				//LOGGER.info("sending - complete [" + DhSectionPos.toString(pos) + "]");
				if (!requestGroup.trySetFullDataSource(fullDataSource))
				{
					fullDataSource.close();
				}
				return;
			}
			
			fullDataSource.close();
			
			if (DhSectionPos.getDetailLevel(pos) > (Config.Common.WorldGenerator.chunkGeneratorMode.get() == EDhApiDistantGeneratorMode.INTERNAL_SERVER
					? DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL
					: this.serverLevel.serverside.fullDataFileHandler.lowestDataDetailLevel()))
			{
				// Make this group unavailable for adding into
				this.requestGroupsByPos.remove(pos, requestGroup);
				if (!requestGroup.tryClose())
				{
					//LOGGER.info("closing [" + DhSectionPos.toString(pos) + "]");
					return;
				}
				
				for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
				{
					//LOGGER.info("sending [" + DhSectionPos.toString(pos) + "] - ["+DhSectionPos.toString(requestData.sectionPos())+"]");
					
					this.requestGroupsByFutureId.remove(requestData.futureId(), requestGroup);
					requestData.releaseRateLimitOnce();
					requestData.message.sendResponse(new SectionRequiresSplittingException());
				}
			}
			else if (requestGroup.isWorldGenTaskComplete())
			{
				//LOGGER.info("sending - retry [" + DhSectionPos.toString(pos) + "]");
				if (!requestGroup.isClosed.get() && !this.closed.get())
				{
					this.tryFulfillDataSourceRequestGroup(requestGroup, pos);
				}
			}
			else
			{
				//LOGGER.info("queueing incomplete world gen [" + DhSectionPos.toString(pos) + "]");
				if (!requestGroup.isClosed.get() && !this.closed.get())
				{
					this.fullDataSourceProvider().queuePositionForRetrieval(pos);
				}
			}
		});
	}
	
	public void onWorldGenTaskComplete(long pos)
	{
		DataSourceRequestGroup requestGroup = this.requestGroupsByPos.get(pos);
		if (requestGroup != null)
		{
			requestGroup.markWorldGenTaskComplete();
			this.tryFulfillDataSourceRequestGroup(requestGroup, pos);
		}
	}
	
	
	
	//=========//
	// ticking //
	//=========//
	
	private void tickLoop()
	{
		try
		{
			while (!Thread.interrupted())
			{
				Thread.sleep(20);
				this.tick();
			}
		}
		catch (InterruptedException ignore) { }
	}
	private void tick()
	{
		if (this.closed.get())
		{
			return;
		}

		// Send finished data source requests
		for (Map.Entry<Long, DataSourceRequestGroup> entry : this.requestGroupsByPos.entrySet())
		{
			DataSourceRequestGroup requestGroup = entry.getValue();
			if (!requestGroup.hasFullDataSource())
			{
				continue;
			}
			
			LOGGER.debug("[" + this.getLevelIdentifier() + "] Fulfilled request group [" + DhSectionPos.toString(entry.getKey()) + "]");
			
			// Make this exact group unavailable for adding into. A replacement group may
			// already exist for the same position, so never remove by key alone.
			if (!this.requestGroupsByPos.remove(entry.getKey(), requestGroup))
			{
				continue;
			}
			if (!requestGroup.tryClose())
			{
				continue;
			}

			FullDataSourceV2 fullDataSource = requestGroup.takeFullDataSource();
			if (fullDataSource == null)
			{
				continue;
			}

			if (requestGroup.requestMessages.isEmpty())
			{
				fullDataSource.close();
				continue;
			}
			
			AbstractExecutorService executor = ThreadPoolUtil.getNetworkCompressionExecutor();
			if (executor == null)
			{
				LOGGER.warn("Unable to send FullDataSourceResponseMessage - getNetworkCompressionExecutor() is null");
				fullDataSource.close();
				for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
				{
					this.requestGroupsByFutureId.remove(requestData.futureId(), requestGroup);
					requestData.releaseRateLimitOnce();
				}
				continue;
			}

			try
			{
				CompletableFuture.runAsync(() ->
				{
					FullDataPayload payload;
					try
					{
						payload = new FullDataPayload(fullDataSource, this.getAllBeamsForPos(entry.getKey()));
					}
					finally
					{
						fullDataSource.close();
					}
				
					for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
					{
						this.requestGroupsByFutureId.remove(requestData.futureId(), requestGroup);

						if (requestData.serverPlayerState.isClosing() || this.closed.get())
						{
							requestData.releaseRateLimitOnce();
							continue;
						}

						requestData.serverPlayerState.fullDataPayloadSender.sendInChunks(payload, () ->
						{
							if (!requestData.serverPlayerState.isClosing())
							{
								requestData.message.sendResponse(new FullDataSourceResponseMessage(payload));
							}
							requestData.releaseRateLimitOnce();
						}, requestData::releaseRateLimitOnce);
					}
				}, executor).exceptionally(throwable ->
				{
					for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
					{
						this.requestGroupsByFutureId.remove(requestData.futureId(), requestGroup);
						requestData.releaseRateLimitOnce();
					}
					return null;
				});
			}
			catch (RejectedExecutionException e)
			{
				fullDataSource.close();
				for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
				{
					this.requestGroupsByFutureId.remove(requestData.futureId(), requestGroup);
					requestData.releaseRateLimitOnce();
				}
			}
		}
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public void close()
	{
		if (!this.closed.compareAndSet(false, true))
		{
			return;
		}

		this.tickerThread.shutdownNow();

		for (SyncRequestState syncRequestState : this.syncRequestsByFutureId.values())
		{
			syncRequestState.cancel();
		}

		for (Long requestId : this.requestGroupsByFutureId.keySet())
		{
			this.cancelGenerationRequest(requestId);
		}

		for (DataSourceRequestGroup requestGroup : this.requestGroupsByPos.values())
		{
			this.requestGroupsByPos.remove(requestGroup.pos, requestGroup);
			if (requestGroup.tryClose())
			{
				FullDataSourceV2 fullDataSource = requestGroup.takeFullDataSource();
				if (fullDataSource != null)
				{
					fullDataSource.close();
				}
				this.fullDataSourceProvider().removeRetrievalRequestIf(pos -> pos == requestGroup.pos);
			}

			for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
			{
				this.requestGroupsByFutureId.remove(requestData.futureId(), requestGroup);
				requestData.releaseRateLimitOnce();
			}
		}

		this.syncRequestsByFutureId.clear();
		this.requestGroupsByFutureId.clear();
		this.requestGroupsByPos.clear();
	}


	private class SyncRequestState
	{
		private final ServerPlayerState serverPlayerState;
		private final long futureId;
		private final ServerPlayerState.RateLimiterSet rateLimiterSet;
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final AtomicBoolean finished = new AtomicBoolean();

		private SyncRequestState(ServerPlayerState serverPlayerState, long futureId, ServerPlayerState.RateLimiterSet rateLimiterSet)
		{
			this.serverPlayerState = serverPlayerState;
			this.futureId = futureId;
			this.rateLimiterSet = rateLimiterSet;
		}

		private boolean isCancelled() { return this.cancelled.get(); }

		private void cancel()
		{
			this.cancelled.set(true);
			this.finish();
		}

		private void finish()
		{
			if (!this.finished.compareAndSet(false, true))
			{
				return;
			}

			FullDataSourceRequestHandler.this.syncRequestsByFutureId.remove(this.futureId, this);
			this.rateLimiterSet.syncOnLoginRateLimiter.release();
		}
	}
	
	
	
}
