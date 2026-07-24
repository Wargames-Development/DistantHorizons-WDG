/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.coreapi;

/** Explicit WDG version construction that keeps the upstream base visible. */
public final class WdgVersionPolicy
{
	public static final String UPSTREAM_BASE_VERSION = "3.0.4-b";

	private WdgVersionPolicy() { }

	public static String developmentVersion()
	{
		return UPSTREAM_BASE_VERSION + "-dev";
	}

	public static String releaseCandidateVersion(int revision)
	{
		return UPSTREAM_BASE_VERSION + "-wdg-rc." + positive(revision);
	}

	public static String stableVersion(int revision)
	{
		return UPSTREAM_BASE_VERSION + "-wdg." + positive(revision);
	}

	public static ReleaseChannel requireSupportedVersion(String version)
	{
		if (developmentVersion().equals(version))
		{
			return ReleaseChannel.DEVELOPMENT;
		}
		if (version != null && version.matches("3\\.0\\.4-b-wdg-rc\\.[1-9][0-9]*"))
		{
			return ReleaseChannel.RELEASE_CANDIDATE;
		}
		if (version != null && version.matches("3\\.0\\.4-b-wdg\\.[1-9][0-9]*"))
		{
			return ReleaseChannel.STABLE;
		}
		throw new IllegalArgumentException("Unsupported Distant Horizons WDG version: " + version);
	}

	private static int positive(int revision)
	{
		if (revision < 1)
		{
			throw new IllegalArgumentException("revision must be positive");
		}
		return revision;
	}
}
