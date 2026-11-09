package com.seibel.distanthorizons.core.world;

import java.util.HashMap;
import java.util.Map;

/** A failed constructor must not run again on every render pass. */
final class LevelLoadRetryGate
{
	private final long retryDelayNanos;
	private final Map<String, Long> lastFailureByLevel = new HashMap<>();

	LevelLoadRetryGate(long retryDelayNanos)
	{
		this.retryDelayNanos = retryDelayNanos;
	}

	synchronized boolean canAttempt(String levelId, long nowNanos)
	{
		Long failedAt = this.lastFailureByLevel.get(levelId);
		return failedAt == null || nowNanos - failedAt >= this.retryDelayNanos;
	}

	/** Returns true only for the first failure until recovery or unload. */
	synchronized boolean recordFailure(String levelId, long nowNanos)
	{
		return this.lastFailureByLevel.put(levelId, nowNanos) == null;
	}

	synchronized void clear(String levelId) { this.lastFailureByLevel.remove(levelId); }
	synchronized void clearAll() { this.lastFailureByLevel.clear(); }
}
