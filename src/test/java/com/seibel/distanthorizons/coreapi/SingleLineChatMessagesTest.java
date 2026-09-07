package com.seibel.distanthorizons.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class SingleLineChatMessagesTest
{
	@Test
	public void splitsEverySupportedLineSeparatorIntoSeparateComponents()
	{
		List<String> submitted = new ArrayList<>();
		SingleLineChatMessages.submitText("first\nsecond\r\nthird\rfourth\n", submitted::add);

		assertEquals(List.of("first", "second", "third", "fourth"), submitted);
	}

	@Test
	public void preservesIntentionalInteriorBlankLines()
	{
		List<String> submitted = new ArrayList<>();
		SingleLineChatMessages.submitText("first\n\nthird", submitted::add);

		assertEquals(List.of("first", "", "third"), submitted);
	}

	@Test
	public void emptyPayloadStillProducesTheRequestedBlankChatLine()
	{
		List<String> submitted = new ArrayList<>();
		SingleLineChatMessages.submitText("", submitted::add);

		assertEquals(List.of(""), submitted);
	}

	@Test
	public void explicitSingleLineSubmissionStillRejectsLiteralLfTokens()
	{
		try
		{
			SingleLineChatMessages.submit(List.of("before LF after"), ignored -> { });
			fail("literal LF token should be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}
}
