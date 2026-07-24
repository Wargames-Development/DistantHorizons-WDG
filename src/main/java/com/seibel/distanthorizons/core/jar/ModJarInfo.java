/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.ReleaseChannel;

import java.io.IOException;
import java.io.InputStream;

/** Runtime access to validated embedded build provenance. */
public final class ModJarInfo
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final String RESOURCE_PATH = "/build_info.json";
	private static final BuildInfo INFO = load();

	public static final String Repository = INFO.repository;
	public static final String Git_Branch = INFO.branchOrChannel;
	public static final String Git_Commit = INFO.commit;
	public static final String Build_Source = INFO.buildSource;
	public static final String Release_Channel = INFO.releaseChannel.name();
	public static final String Updater_Policy = INFO.updaterPolicy.name();
	public static final String Tree_State = INFO.treeState;
	public static final String Source_Tree_Digest = INFO.sourceTreeDigest;

	private ModJarInfo() { }

	public static BuildInfo getBuildInfo() { return INFO; }

	private static BuildInfo load()
	{
		try (InputStream input = ModJarInfo.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (input == null)
			{
				return missingResource();
			}
			return BuildInfoResourceLoader.load(input, ModInfo.VERSION);
		}
		catch (IOException | RuntimeException e)
		{
			if (ModInfo.RELEASE_CHANNEL == ReleaseChannel.DEVELOPMENT)
			{
				LOGGER.warn("Unable to load valid build provenance from [{}]; using a development fallback: {}", RESOURCE_PATH, e.getMessage());
				return developmentFallback();
			}
			LOGGER.error("Production Distant Horizons build provenance is invalid: {}", e.getMessage());
			throw new IllegalStateException("Production build requires valid " + RESOURCE_PATH, e);
		}
	}

	private static BuildInfo missingResource()
	{
		if (ModInfo.RELEASE_CHANNEL == ReleaseChannel.DEVELOPMENT)
		{
			LOGGER.warn("Build provenance resource [{}] is absent; using a development fallback.", RESOURCE_PATH);
			return developmentFallback();
		}
		throw new IllegalStateException("Production build provenance resource is missing: " + RESOURCE_PATH);
	}

	private static BuildInfo developmentFallback()
	{
		return new BuildInfo(
			BuildInfo.CURRENT_SCHEMA_VERSION,
			ModInfo.ID,
			ModInfo.VERSION,
			ReleaseChannel.DEVELOPMENT,
			"upstream-development-environment",
			"Distant-Horizons-Team/DistantHorizons",
			"development",
			"0000000000000000000000000000000000000000",
			"0000000",
			"modified",
			"0000000000000000000000000000000000000000000000000000000000000000",
			"DEVELOPMENT_FALLBACK",
			"1.7.10",
			"10.13.4.1614",
			65,
			"development",
			UpdaterPolicy.UPSTREAM,
			false
		);
	}
}
