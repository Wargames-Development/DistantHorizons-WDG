package com.seibel.distanthorizons.core.jar.updater;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.seibel.distanthorizons.core.jar.UpdaterPolicy;
import org.junit.Test;

public class UpdaterPolicyDecisionTest
{
	@Test
	public void managedBuildIgnoresEnabledConfigAndSilentUpdate()
	{
		assertFalse(UpdaterPolicyDecision.allowAutoUpdater(UpdaterPolicy.MANAGED_DISABLED, true));
		assertFalse(UpdaterPolicyDecision.allowSilentUpdater(UpdaterPolicy.MANAGED_DISABLED, true));
	}

	@Test
	public void upstreamBehaviourRemainsReachable()
	{
		assertTrue(UpdaterPolicyDecision.allowAutoUpdater(UpdaterPolicy.UPSTREAM, true));
		assertFalse(UpdaterPolicyDecision.allowAutoUpdater(UpdaterPolicy.UPSTREAM, false));
	}
}
