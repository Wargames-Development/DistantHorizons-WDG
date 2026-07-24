package com.seibel.distanthorizons.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.seibel.distanthorizons.core.enums.MinecraftTextFormat;

import org.junit.Test;

public class BuildWarningMessagesTest
{
	@Test
	public void developmentWarningIsThreeOrderedSingleLines()
	{
		List<String> lines = BuildWarningMessages.forBuild(ReleaseChannel.DEVELOPMENT, "3.0.4-b-dev");
		assertEquals(3, lines.size());
		assertTrue(lines.get(0).contains("nightly/unstable"));
		assertTrue(lines.get(0).startsWith(MinecraftTextFormat.DARK_GREEN));
		assertTrue(lines.get(0).endsWith(MinecraftTextFormat.CLEAR_FORMATTING));
		assertEquals("Issues may occur with this version.", lines.get(1));
		assertEquals("Here be dragons!", lines.get(2));
		assertSingleLine(lines);
	}

	@Test
	public void releaseCandidateWarningIsThreeOrderedSingleLines()
	{
		List<String> lines = BuildWarningMessages.forBuild(ReleaseChannel.RELEASE_CANDIDATE, "3.0.4-b-wdg-rc.1");
		assertEquals(3, lines.size());
		assertTrue(lines.get(0).contains("WDG release candidate"));
		assertTrue(lines.get(0).startsWith(MinecraftTextFormat.DARK_GREEN));
		assertTrue(lines.get(0).endsWith(MinecraftTextFormat.CLEAR_FORMATTING));
		assertTrue(lines.get(1).contains("modpack validation"));
		assertTrue(lines.get(2).contains("release manifest"));
		assertSingleLine(lines);
	}

	@Test
	public void stableBuildHasNoUnstableWarning()
	{
		assertTrue(BuildWarningMessages.forBuild(ReleaseChannel.STABLE, "3.0.4-b-wdg.1").isEmpty());
	}

	@Test
	public void submitterUsesOneCallPerLine()
	{
		List<String> lines = BuildWarningMessages.forBuild(ReleaseChannel.RELEASE_CANDIDATE, "3.0.4-b-wdg-rc.1");
		List<String> submitted = new ArrayList<>();
		SingleLineChatMessages.submit(lines, submitted::add);
		assertEquals(lines, submitted);
		assertEquals(3, submitted.size());
	}

	private static void assertSingleLine(List<String> lines)
	{
		for (String line : lines)
		{
			assertFalse(line.contains("\n"));
			assertFalse(line.contains("\r"));
			assertFalse(line.contains("\\n"));
			assertFalse(line.contains("\\r"));
			assertFalse(line.matches(".*(^|[^A-Za-z])LF([^A-Za-z]|$).*$"));
		}
	}
}
