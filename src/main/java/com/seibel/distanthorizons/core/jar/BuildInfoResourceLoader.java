/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Null-safe classpath resource boundary for build_info.json. */
public final class BuildInfoResourceLoader
{
	private BuildInfoResourceLoader() { }

	public static BuildInfo load(InputStream input, String expectedVersion) throws IOException
	{
		if (input == null)
		{
			throw new IllegalStateException("build_info.json resource stream is missing");
		}
		return BuildInfoParser.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), expectedVersion);
	}
}
