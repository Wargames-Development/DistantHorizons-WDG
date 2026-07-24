/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.config.file;

/** Pure policy used to preserve compatible existing configuration files. */
public final class ConfigVersionPolicy
{
	public enum Action
	{
		LOAD_EXISTING,
		LOAD_NEWER_WITH_WARNING,
		RESET_OLDER
	}

	private ConfigVersionPolicy() { }

	public static Action actionFor(int existingVersion, int currentVersion)
	{
		if (existingVersion == currentVersion)
		{
			return Action.LOAD_EXISTING;
		}
		if (existingVersion > currentVersion)
		{
			return Action.LOAD_NEWER_WITH_WARNING;
		}
		return Action.RESET_OLDER;
	}
}
