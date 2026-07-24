package com.seibel.distanthorizons.core.jar.updater;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.seibel.distanthorizons.core.jar.UpdaterPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class UpdaterExecutionGateTest
{
	@Test
	public void everyWdgChannelPolicySkipsAllUpstreamServicesEvenWhenConfigIsTrue()
	{
		AtomicInteger modrinth = new AtomicInteger();
		AtomicInteger gitlab = new AtomicInteger();
		boolean result = UpdaterExecutionGate.execute(
			UpdaterPolicy.MANAGED_DISABLED,
			true,
			true,
			() -> { modrinth.incrementAndGet(); return true; },
			() -> { gitlab.incrementAndGet(); return true; }
		);
		assertFalse(result);
		assertTrue(modrinth.get() == 0);
		assertTrue(gitlab.get() == 0);
	}

	@Test
	public void nonManagedBehaviourRemainsReachable()
	{
		AtomicInteger stable = new AtomicInteger();
		assertTrue(UpdaterExecutionGate.execute(
			UpdaterPolicy.UPSTREAM,
			true,
			true,
			() -> { stable.incrementAndGet(); return true; },
			() -> false
		));
		assertTrue(stable.get() == 1);
	}

	@Test
	public void managedGatePreventsEveryDownstreamUpdaterSideEffect()
	{
		AtomicInteger modrinth = new AtomicInteger();
		AtomicInteger gitlab = new AtomicInteger();
		AtomicInteger updatePath = new AtomicInteger();
		AtomicInteger downloader = new AtomicInteger();
		AtomicInteger deletion = new AtomicInteger();
		AtomicInteger updateUi = new AtomicInteger();
		boolean result = UpdaterExecutionGate.execute(
			UpdaterPolicy.MANAGED_DISABLED,
			true,
			true,
			() ->
			{
				modrinth.incrementAndGet();
				updatePath.incrementAndGet();
				downloader.incrementAndGet();
				deletion.incrementAndGet();
				updateUi.incrementAndGet();
				return true;
			},
			() ->
			{
				gitlab.incrementAndGet();
				return true;
			}
		);
		assertFalse(result);
		assertTrue(modrinth.get() == 0);
		assertTrue(gitlab.get() == 0);
		assertTrue(updatePath.get() == 0);
		assertTrue(downloader.get() == 0);
		assertTrue(deletion.get() == 0);
		assertTrue(updateUi.get() == 0);
	}
}
