/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiQualityPreset;
import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiThreadPreset;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;

/** Conservative defaults used only when a new WDG configuration is generated. */
public final class WdgFreshProfileDefaults
{
	public static final EDhApiQualityPreset QUALITY_PRESET = EDhApiQualityPreset.LOW;
	public static final EDhApiThreadPreset THREAD_PRESET = EDhApiThreadPreset.MINIMAL_IMPACT;
	public static final int LOD_RENDER_DISTANCE_RADIUS = 128;
	public static final boolean DISTANT_GENERATION_ENABLED = true;
	public static final EDhApiDistantGeneratorMode GENERATOR_MODE = EDhApiDistantGeneratorMode.SURFACE;
	public static final boolean AUTO_UPDATER_ENABLED = false;
	public static final boolean SILENT_UPDATER_ENABLED = false;

	private WdgFreshProfileDefaults() { }
}
