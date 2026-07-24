package com.seibel.distanthorizons.core.jar;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class BuildInfoResourceLoaderTest
{
	@Test
	public void missingResourceIsRejectedBeforeReaderCreation()
	{
		assertThrows(IllegalStateException.class, () -> BuildInfoResourceLoader.load(null, "3.0.4-b-wdg-rc.1"));
	}
}
