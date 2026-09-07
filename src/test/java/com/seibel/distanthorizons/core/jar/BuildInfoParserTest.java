package com.seibel.distanthorizons.core.jar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BuildInfoParserTest
{
	private static final String VERSION = "3.0.4-b-wdg-rc.1";

	@Test
	public void parsesReleaseCandidateAndAssignsBranchAndCommitCorrectly()
	{
		BuildInfo info = BuildInfoParser.parse(validJson(), VERSION);
		assertEquals("master", info.branchOrChannel);
		assertEquals("170c809b2befbb3c54bd143ac68f8a9329e41f07", info.commit);
		assertEquals(UpdaterPolicy.MANAGED_DISABLED, info.updaterPolicy);
		assertTrue(info.isWdgManaged());
	}


	@Test
	public void parsesValidDevelopmentProvenance()
	{
		String development = validJson()
			.replace("3.0.4-b-wdg-rc.1", "3.0.4-b-dev")
			.replace("RELEASE_CANDIDATE", "DEVELOPMENT")
			.replace("Wargames-Development/DistantHorizons-WDG", "DarkShadow44/DistantHorizonsStandalone")
			.replace("UNCOMMITTED_VALIDATION", "DEVELOPMENT_ENVIRONMENT")
			.replace("MANAGED_DISABLED", "UPSTREAM")
			.replace("change-007-rc-v1", "development");
		BuildInfo info = BuildInfoParser.parse(development, "3.0.4-b-dev");
		assertEquals(com.seibel.distanthorizons.coreapi.ReleaseChannel.DEVELOPMENT, info.releaseChannel);
		assertEquals(UpdaterPolicy.UPSTREAM, info.updaterPolicy);
	}

	@Test
	public void rejectsMalformedMissingWrongSchemaWrongModAndVersion()
	{
		assertThrows(IllegalArgumentException.class, () -> BuildInfoParser.parse("{", VERSION));
		assertThrows(IllegalArgumentException.class, () -> BuildInfoParser.parse(validJson().replace("\"repository\"", "\"missingRepository\""), VERSION));
		assertThrows(IllegalArgumentException.class, () -> BuildInfoParser.parse(validJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"), VERSION));
		assertThrows(IllegalArgumentException.class, () -> BuildInfoParser.parse(validJson().replace("\"distanthorizons\"", "\"other\""), VERSION));
		assertThrows(IllegalArgumentException.class, () -> BuildInfoParser.parse(validJson(), "3.0.4-b-dev"));
	}

	@Test
	public void parserDoesNotPrintExceptions()
	{
		PrintStream oldOut = System.out;
		PrintStream oldErr = System.err;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		try
		{
			System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
			assertThrows(IllegalArgumentException.class, () -> BuildInfoParser.parse("{", VERSION));
		}
		finally
		{
			System.setOut(oldOut);
			System.setErr(oldErr);
		}
		assertEquals("", out.toString(StandardCharsets.UTF_8));
		assertEquals("", err.toString(StandardCharsets.UTF_8));
	}

	private static String validJson()
	{
		return "{\n"
			+ "\"schemaVersion\": 1,\n"
			+ "\"modId\": \"distanthorizons\",\n"
			+ "\"modVersion\": \"3.0.4-b-wdg-rc.1\",\n"
			+ "\"releaseChannel\": \"RELEASE_CANDIDATE\",\n"
			+ "\"repository\": \"Wargames-Development/DistantHorizons-WDG\",\n"
			+ "\"upstreamRepository\": \"DarkShadow44/DistantHorizonsStandalone\",\n"
			+ "\"branchOrChannel\": \"master\",\n"
			+ "\"commit\": \"170c809b2befbb3c54bd143ac68f8a9329e41f07\",\n"
			+ "\"shortCommit\": \"170c809b2bef\",\n"
			+ "\"treeState\": \"modified\",\n"
			+ "\"sourceTreeDigest\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n"
			+ "\"buildSource\": \"UNCOMMITTED_VALIDATION\",\n"
			+ "\"minecraftVersion\": \"1.7.10\",\n"
			+ "\"forgeVersion\": \"10.13.4.1614\",\n"
			+ "\"javaClassFileTarget\": 65,\n"
			+ "\"packageContractVersion\": \"change-007-rc-v1\",\n"
			+ "\"updaterPolicy\": \"MANAGED_DISABLED\",\n"
			+ "\"reproducibleBuild\": true\n"
			+ "}\n";
	}
}
