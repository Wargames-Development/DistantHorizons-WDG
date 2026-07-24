/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar.updater;

import com.seibel.distanthorizons.core.jar.ModJarInfo;
import com.seibel.distanthorizons.core.jar.UpdaterPolicy;

/** Applies the embedded build-provenance updater policy before any updater service is touched. */
public final class UpdaterPolicyManager
{
	private UpdaterPolicyManager() { }

	public static UpdaterPolicy getPolicy()
	{
		return ModJarInfo.getBuildInfo().updaterPolicy;
	}

	public static boolean allowsUpstreamUpdater()
	{
		return getPolicy().allowsUpstreamUpdater();
	}

	public static boolean effectiveAutoUpdaterEnabled(boolean configuredValue)
	{
		return UpdaterPolicyDecision.allowAutoUpdater(getPolicy(), configuredValue);
	}

	public static boolean effectiveSilentUpdaterEnabled(boolean configuredValue)
	{
		return UpdaterPolicyDecision.allowSilentUpdater(getPolicy(), configuredValue);
	}

	public static String managedDisabledMessage()
	{
		return "Distant Horizons updates are managed by the WDG modpack; upstream self-update services are disabled.";
	}
}
