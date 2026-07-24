package com.seibel.distanthorizons.core.config.file;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConfigVersionPolicyTest
{
	@Test
	public void change006VersionFourConfigLoadsWithoutReset()
	{
		assertEquals(
			ConfigVersionPolicy.Action.LOAD_EXISTING,
			ConfigVersionPolicy.actionFor(4, 4)
		);
	}

	@Test
	public void onlyOlderIncompatibleConfigResets()
	{
		assertEquals(ConfigVersionPolicy.Action.RESET_OLDER, ConfigVersionPolicy.actionFor(3, 4));
		assertEquals(ConfigVersionPolicy.Action.LOAD_NEWER_WITH_WARNING, ConfigVersionPolicy.actionFor(5, 4));
	}
}
