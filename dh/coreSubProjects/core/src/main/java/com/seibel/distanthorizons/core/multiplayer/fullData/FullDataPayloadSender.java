package com.seibel.distanthorizons.core.multiplayer.fullData;

import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.TimerUtil;
import io.netty.buffer.ByteBuf;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

public class FullDataPayloadSender implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final int TICK_RATE = 20;
	
	/** 1 Mebibyte minus 576 bytes for other info */
	public static final int FULL_DATA_SPLIT_SIZE_IN_BYTES = 1_048_000;
	
	
	private static final Timer UPLOAD_TIMER = TimerUtil.CreateTimer("FullDataPayloadSender");
	private final TimerTask tickTimerTask = TimerUtil.createTimerTask(this::tick);
	
	private final NetworkSession session;
	private final IntSupplier maxKBpsSupplier;
	private final ConcurrentLinkedQueue<PendingTransfer> transferQueue = new ConcurrentLinkedQueue<>();
	
	private final SharedBandwidthLimit sharedBandwidthLimit;
	private final AtomicBoolean closed = new AtomicBoolean();
	
	
	public FullDataPayloadSender(NetworkSession session, IntSupplier maxKBpsSupplier, SharedBandwidthLimit sharedBandwidthLimit)
	{
		this.session = session;
		this.maxKBpsSupplier = maxKBpsSupplier;
		this.sharedBandwidthLimit = sharedBandwidthLimit;
		UPLOAD_TIMER.scheduleAtFixedRate(this.tickTimerTask, 0, 1000 / TICK_RATE);
	}
	
	@Override
	public void close()
	{
		if (!this.closed.compareAndSet(false, true))
		{
			return;
		}

		this.tickTimerTask.cancel();
		UPLOAD_TIMER.purge();
		this.sharedBandwidthLimit.setSenderActive(this, false);

		PendingTransfer pendingTransfer;
		while ((pendingTransfer = this.transferQueue.poll()) != null)
		{
			pendingTransfer.discard();
		}
	}
	
	
	public void sendInChunks(FullDataPayload payload, Runnable sendFinalMessage)
	{
		this.sendInChunks(payload, sendFinalMessage, () -> { });
	}

	public void sendInChunks(FullDataPayload payload, Runnable sendFinalMessage, Runnable discardMessage)
	{
		PendingTransfer pendingTransfer = new PendingTransfer(payload, sendFinalMessage, discardMessage);
		if (this.closed.get())
		{
			pendingTransfer.discard();
			return;
		}

		this.transferQueue.add(pendingTransfer);

		// close() may have raced with the queue insertion. If so, remove this exact
		// transfer and run its discard callback so request permits are not leaked.
		if (this.closed.get() && this.transferQueue.remove(pendingTransfer))
		{
			pendingTransfer.discard();
		}
	}
	
	private void tick()
	{
		if (this.closed.get())
		{
			this.sharedBandwidthLimit.setSenderActive(this, false);
			return;
		}

		boolean hasPendingTransfers = !this.transferQueue.isEmpty();
		this.sharedBandwidthLimit.setSenderActive(this, hasPendingTransfers);
		if (!hasPendingTransfers)
		{
			return;
		}

		int bandwidthShare = this.sharedBandwidthLimit.getBandwidthShare();
		int maxPlayerRate = Math.min(this.maxKBpsSupplier.getAsInt(), bandwidthShare);
		
		// + 1 to account for rounding errors on values of < 4
		int bytesToSend = maxPlayerRate > 0
				? (maxPlayerRate * 1000) / TICK_RATE + 1
				: Integer.MAX_VALUE;
		
		while (bytesToSend > 0 && !this.closed.get())
		{
			PendingTransfer pendingTransfer = this.transferQueue.peek();
			if (pendingTransfer == null)
			{
				return;
			}
			if (this.closed.get())
			{
				if (this.transferQueue.remove(pendingTransfer))
				{
					pendingTransfer.discard();
				}
				return;
			}
			
			int chunkSize = Math.min(Math.min(bytesToSend, FULL_DATA_SPLIT_SIZE_IN_BYTES), pendingTransfer.buffer.readableBytes());
			boolean isFirstChunk = pendingTransfer.buffer.readerIndex() == 0;
			
			FullDataSplitMessage chunkMessage = new FullDataSplitMessage(pendingTransfer.bufferId, pendingTransfer.buffer.readSlice(chunkSize).retain(), isFirstChunk);
			this.session.sendMessage(chunkMessage);
			
			bytesToSend -= chunkSize;
			
			if (pendingTransfer.buffer.readableBytes() == 0)
			{
				this.transferQueue.remove(pendingTransfer);
				pendingTransfer.complete();
			}
		}
	}
	
	
	private static class PendingTransfer
	{
		public final int bufferId;
		public final ByteBuf buffer;
		public final Runnable sendFinalMessage;
		public final Runnable discardMessage;
		private final AtomicBoolean resolved = new AtomicBoolean();
		
		private PendingTransfer(FullDataPayload payload, Runnable sendFinalMessage, Runnable discardMessage)
		{
			this.bufferId = payload.dtoBufferId;
			this.buffer = payload.dtoBuffer.duplicate().readerIndex(0);
			this.sendFinalMessage = sendFinalMessage;
			this.discardMessage = discardMessage;
		}

		private void complete()
		{
			if (!this.resolved.compareAndSet(false, true))
			{
				return;
			}

			try
			{
				this.sendFinalMessage.run();
			}
			catch (Throwable e)
			{
				LOGGER.error("Failed to send the completed full data transfer", e);
			}
		}

		private void discard()
		{
			if (!this.resolved.compareAndSet(false, true))
			{
				return;
			}

			try
			{
				this.discardMessage.run();
			}
			catch (Throwable e)
			{
				LOGGER.error("Failed to discard an incomplete full data transfer", e);
			}
		}
		
	}
	
}
