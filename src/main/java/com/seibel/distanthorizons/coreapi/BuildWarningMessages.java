/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.coreapi;

import com.seibel.distanthorizons.core.enums.MinecraftTextFormat;

import java.util.Collections;
import java.util.List;

/** Builds immutable, single-line startup warning messages for the current release channel. */
public final class BuildWarningMessages
{
	private BuildWarningMessages() { }

	public static List<String> forCurrentBuild()
	{
		return forBuild(ModInfo.RELEASE_CHANNEL, ModInfo.VERSION);
	}

	public static List<String> forBuild(ReleaseChannel channel, String version)
	{
		if (channel == null)
		{
			throw new IllegalArgumentException("channel is null");
		}
		if (version == null || version.trim().isEmpty())
		{
			throw new IllegalArgumentException("version is empty");
		}

		List<String> lines;
		switch (channel)
		{
			case DEVELOPMENT:
				lines = List.of(
					MinecraftTextFormat.DARK_GREEN + "Distant Horizons: nightly/unstable build, version: [" + version + "]." + MinecraftTextFormat.CLEAR_FORMATTING,
					"Issues may occur with this version.",
					"Here be dragons!"
				);
				break;
			case RELEASE_CANDIDATE:
				lines = List.of(
					MinecraftTextFormat.DARK_GREEN + "Distant Horizons WDG release candidate, version: [" + version + "]." + MinecraftTextFormat.CLEAR_FORMATTING,
					"This build is undergoing modpack validation and may still have issues.",
					"Use the WDG release manifest and logs when reporting problems."
				);
				break;
			case STABLE:
				return Collections.emptyList();
			default:
				throw new IllegalStateException("Unhandled release channel: " + channel);
		}

		for (String line : lines)
		{
			validateLine(line);
		}
		return Collections.unmodifiableList(lines);
	}

	private static void validateLine(String line)
	{
		if (line == null || line.isEmpty())
		{
			throw new IllegalStateException("Warning line is empty");
		}
		if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0 || line.contains("\\n") || line.contains("\\r"))
		{
			throw new IllegalStateException("Warning line contains a line separator");
		}
		if (line.matches(".*(^|[^A-Za-z])LF([^A-Za-z]|$).*$"))
		{
			throw new IllegalStateException("Warning line contains a literal LF separator token");
		}
	}
}
