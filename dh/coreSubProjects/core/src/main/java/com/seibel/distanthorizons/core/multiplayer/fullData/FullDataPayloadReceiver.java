package com.seibel.distanthorizons.core.multiplayer.fullData;

import com.google.common.cache.CacheBuilder;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;
import com.seibel.distanthorizons.core.render.TerrainCacheDiagnostics;

public class FullDataPayloadReceiver implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logNetworkEventToFile)
			.build();
	
	private final ConcurrentMap<Integer, CompositeByteBuf> buffersById = CacheBuilder.newBuilder()
		.expireAfterAccess(30, TimeUnit.SECONDS)
			.<Integer, CompositeByteBuf>build().asMap();
	private final LongAdder receivedBytes = new LongAdder();
	private final LongAdder receivedFragments = new LongAdder();
	private final LongAdder decodedSources = new LongAdder();
	
	@Override
	public void close()
	{
		this.buffersById.clear();
	}
	
	public void receiveChunk(FullDataSplitMessage message)
	{
		if (TerrainCacheDiagnostics.ENABLED)
		{
			receivedFragments.increment();
			receivedBytes.add(message.buffer.writerIndex());
		}
		this.buffersById.compute(message.bufferId, (bufferId, composite) ->
		{
			if (message.isFirst)
			{
				composite = Unpooled.compositeBuffer();
				LOGGER.debug("Created new full data buffer [" + message.bufferId + "]: [" + composite + "]");
			}
			else if (composite == null)
			{
				LOGGER.debug("Received non-first full data chunk for empty buffer [" + message.bufferId + "]: [" + message.buffer + "].");
				return null;
			}
			
			message.buffer.readerIndex(0);
			composite.addComponent(message.buffer);
			composite.writerIndex(composite.writerIndex() + message.buffer.writerIndex());
			LOGGER.debug("Updated full data buffer [" + message.bufferId + "]: [" + composite + "].");
			return composite;
		});
	}
	
	public FullDataSourceV2DTO decodeDataSource(FullDataPayload payload)
	{
		CompositeByteBuf compositeByteBuffer = this.buffersById.get(payload.dtoBufferId);
		Objects.requireNonNull(compositeByteBuffer, "Unable to get a complete buffer for a received payload. Ignore this if it doesn't spam similar errors");
		
		try
		{
			FullDataSourceV2DTO dataSourceDto = INetworkObject.decodeToInstance(FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding(), compositeByteBuffer);
			if (TerrainCacheDiagnostics.ENABLED) decodedSources.increment();
			return dataSourceDto;
		}
		finally
		{
			// Releasing the buffer is handled by cache
			this.buffersById.remove(payload.dtoBufferId);
		}
	}

	public void addTerrainDownloadMetrics(Map<String, Long> output)
	{
		output.put("download_fragment_bytes_total", receivedBytes.sum());
		output.put("download_fragments_total", receivedFragments.sum());
		output.put("download_payloads_decoded_total", decodedSources.sum());
	}
	
}
