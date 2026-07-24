package com.seibel.distanthorizons.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class WdgVersionPolicyTest
{
	@Test
	public void constructsExactDevelopmentRcAndStableVersions()
	{
		assertEquals("3.0.4-b-dev", WdgVersionPolicy.developmentVersion());
		assertEquals("3.0.4-b-wdg-rc.1", WdgVersionPolicy.releaseCandidateVersion(1));
		assertEquals("3.0.4-b-wdg.1", WdgVersionPolicy.stableVersion(1));
	}

	@Test
	public void rejectsUnsupportedOrZeroRevisionVersions()
	{
		assertThrows(IllegalArgumentException.class, () -> WdgVersionPolicy.releaseCandidateVersion(0));
		assertThrows(IllegalArgumentException.class, () -> WdgVersionPolicy.requireSupportedVersion("3.0.4-b-wdg-rc.0"));
		assertThrows(IllegalArgumentException.class, () -> WdgVersionPolicy.requireSupportedVersion("3.0.5"));
	}
}
