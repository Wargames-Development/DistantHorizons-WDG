/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar.updater;

import com.seibel.distanthorizons.core.jar.UpdaterPolicy;

import java.util.function.BooleanSupplier;

/** Testable seam proving managed builds cannot reach upstream update services. */
public final class UpdaterExecutionGate
{
	private UpdaterExecutionGate() { }

	public static boolean execute(
		UpdaterPolicy policy,
		boolean configuredEnabled,
		boolean stableBranch,
		BooleanSupplier stableService,
		BooleanSupplier nightlyService)
	{
		if (!UpdaterPolicyDecision.allowAutoUpdater(policy, configuredEnabled))
		{
			return false;
		}
		if (stableService == null || nightlyService == null)
		{
			throw new IllegalArgumentException("Updater services are required when upstream updating is allowed");
		}
		return stableBranch ? stableService.getAsBoolean() : nightlyService.getAsBoolean();
	}
}
