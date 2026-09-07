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

	/**
	 * Splits a legacy multiline chat payload into individual Minecraft chat components.
	 * This is required for Minecraft 1.7.10, where embedded line feeds may render as literal
	 * {@code LF} text instead of producing a new chat line.
	 */
	public static void submitText(String text, Consumer<String> sink)
	{
		if (text == null || sink == null)
		{
			throw new IllegalArgumentException("text and sink are required");
		}

		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		if (normalized.isEmpty())
		{
			sink.accept("");
			return;
		}

		String[] lines = normalized.split("\n", -1);
		int lineCount = lines.length;
		while (lineCount > 1 && lines[lineCount - 1].isEmpty())
		{
			lineCount--;
		}

		for (int index = 0; index < lineCount; index++)
		{
			String line = lines[index];
			if (!line.isEmpty())
			{
				validate(line);
			}
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
