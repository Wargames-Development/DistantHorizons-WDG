package com.seibel.distanthorizons.core.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiQualityPreset;
import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiThreadPreset;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.junit.Test;

public class WdgFreshProfileDefaultsTest
{
	@Test
	public void conservativeProfileIsExactAndConfigFormatIsUnchanged()
	{
		assertEquals(EDhApiQualityPreset.LOW, WdgFreshProfileDefaults.QUALITY_PRESET);
		assertEquals(EDhApiThreadPreset.MINIMAL_IMPACT, WdgFreshProfileDefaults.THREAD_PRESET);
		assertEquals(128, WdgFreshProfileDefaults.LOD_RENDER_DISTANCE_RADIUS);
		assertTrue(WdgFreshProfileDefaults.DISTANT_GENERATION_ENABLED);
		assertEquals(EDhApiDistantGeneratorMode.SURFACE, WdgFreshProfileDefaults.GENERATOR_MODE);
		assertFalse(WdgFreshProfileDefaults.AUTO_UPDATER_ENABLED);
		assertFalse(WdgFreshProfileDefaults.SILENT_UPDATER_ENABLED);
		assertEquals(4, ModInfo.CONFIG_FILE_VERSION);
	}
}
