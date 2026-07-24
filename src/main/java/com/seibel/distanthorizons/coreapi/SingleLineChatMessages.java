/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.coreapi;

import java.util.List;
import java.util.function.Consumer;

/** Submits an ordered set of chat lines without allowing multiline components. */
public final class SingleLineChatMessages
{
	private SingleLineChatMessages() { }

	public static void submit(List<String> lines, Consumer<String> sink)
	{
		if (lines == null || sink == null)
		{
			throw new IllegalArgumentException("lines and sink are required");
		}
		for (String line : lines)
		{
			validate(line);
			sink.accept(line);
		}
	}

	public static void validate(String line)
	{
		if (line == null || line.isEmpty())
		{
			throw new IllegalArgumentException("Chat line is empty");
		}
		if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0 || line.contains("\\n") || line.contains("\\r"))
		{
			throw new IllegalArgumentException("Chat line contains a line separator");
		}
		if (line.matches(".*(^|[^A-Za-z])LF([^A-Za-z]|$).*$"))
		{
			throw new IllegalArgumentException("Chat line contains a literal LF separator token");
		}
	}
}
