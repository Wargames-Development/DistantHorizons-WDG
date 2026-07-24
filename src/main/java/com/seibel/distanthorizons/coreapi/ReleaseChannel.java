/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.coreapi;

import java.util.Locale;

/** Explicit release semantics for WDG and upstream Distant Horizons builds. */
public enum ReleaseChannel
{
	DEVELOPMENT,
	RELEASE_CANDIDATE,
	STABLE;

	public static ReleaseChannel fromVersion(String version)
	{
		if (version == null || version.trim().isEmpty())
		{
			throw new IllegalArgumentException("version is empty");
		}
		String normalized = version.toLowerCase(Locale.ROOT);
		if (normalized.contains("-wdg-rc."))
		{
			return RELEASE_CANDIDATE;
		}
		if (normalized.endsWith("-dev") || normalized.contains("-dev.") || normalized.contains("-snapshot"))
		{
			return DEVELOPMENT;
		}
		return STABLE;
	}

	public boolean isDevelopment() { return this == DEVELOPMENT; }
	public boolean isReleaseCandidate() { return this == RELEASE_CANDIDATE; }
	public boolean isStable() { return this == STABLE; }
}
