package com.seibel.distanthorizons.core.world;

import org.junit.Test;
import static org.junit.Assert.*;

public class LevelLoadRetryGateTest
{
	@Test public void renderPassesWaitForRetryAndOnlyFirstFailureNotifies()
	{
		LevelLoadRetryGate gate = new LevelLoadRetryGate(30_000);
		assertTrue(gate.canAttempt("DIM5", 0));
		assertTrue(gate.recordFailure("DIM5", 0));
		for (long now = 0; now < 30_000; now++) assertFalse(gate.canAttempt("DIM5", now));
		assertTrue(gate.canAttempt("DIM5", 30_000));
		assertFalse(gate.recordFailure("DIM5", 30_000));
		assertFalse(gate.canAttempt("DIM5", 59_999));
		assertTrue(gate.canAttempt("DIM5", 60_000));
	}

	@Test public void otherLevelsRecoveryUnloadAndReconnectCanAttemptImmediately()
	{
		LevelLoadRetryGate gate = new LevelLoadRetryGate(30_000);
		gate.recordFailure("DIM5", 0);
		assertTrue(gate.canAttempt("DIM2", 1));
		gate.clear("DIM5");
		assertTrue(gate.canAttempt("DIM5", 1));
		assertTrue(gate.recordFailure("DIM5", 1));
		gate.recordFailure("DIM2", 1);
		gate.clearAll();
		assertTrue(gate.canAttempt("DIM5", 2));
		assertTrue(gate.canAttempt("DIM2", 2));
	}
}
