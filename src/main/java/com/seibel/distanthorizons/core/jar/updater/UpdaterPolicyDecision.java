/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar.updater;

import com.seibel.distanthorizons.core.jar.UpdaterPolicy;

/** Pure updater-policy decision logic used by runtime code and focused tests. */
public final class UpdaterPolicyDecision
{
	private UpdaterPolicyDecision() { }

	public static boolean allowAutoUpdater(UpdaterPolicy policy, boolean configuredValue)
	{
		return policy != null && policy.allowsUpstreamUpdater() && configuredValue;
	}

	public static boolean allowSilentUpdater(UpdaterPolicy policy, boolean configuredValue)
	{
		return allowAutoUpdater(policy, configuredValue);
	}
}
