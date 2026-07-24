package com.seibel.distanthorizons.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReleaseChannelTest
{
	@Test
	public void parsesSupportedVersionPolicies()
	{
		assertEquals(ReleaseChannel.DEVELOPMENT, ReleaseChannel.fromVersion("3.0.4-b-dev"));
		assertEquals(ReleaseChannel.RELEASE_CANDIDATE, ReleaseChannel.fromVersion("3.0.4-b-wdg-rc.1"));
		assertEquals(ReleaseChannel.STABLE, ReleaseChannel.fromVersion("3.0.4-b-wdg.1"));
		assertTrue(ReleaseChannel.DEVELOPMENT.isDevelopment());
		assertFalse(ReleaseChannel.RELEASE_CANDIDATE.isDevelopment());
	}
}
