/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar;

import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.ReleaseChannel;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, dependency-free parser for the deterministic flat build_info.json schema. */
public final class BuildInfoParser
{
	private static final Pattern FIELD_NAME = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:");
	private static final Pattern STRING_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
	private static final Pattern INTEGER_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*([0-9]+)");
	private static final Pattern BOOLEAN_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(true|false)");
	private static final Set<String> EXPECTED_FIELDS = Set.of(
		"schemaVersion", "modId", "modVersion", "releaseChannel", "repository", "upstreamRepository",
		"branchOrChannel", "commit", "shortCommit", "treeState", "sourceTreeDigest", "buildSource",
		"minecraftVersion", "forgeVersion", "javaClassFileTarget", "packageContractVersion", "updaterPolicy",
		"reproducibleBuild"
	);
	private static final String WDG_REPOSITORY = "Wargames-Development/DistantHorizons-WDG";
	private static final String UPSTREAM_REPOSITORY = "DarkShadow44/DistantHorizonsStandalone";
	private static final String PACKAGE_CONTRACT = "change-007-rc-v1";

	private BuildInfoParser() { }

	public static BuildInfo parse(String json, String expectedVersion)
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new IllegalArgumentException("build_info.json is empty");
		}
		String trimmed = json.trim();
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
		{
			throw new IllegalArgumentException("build_info.json is malformed");
		}
		if (expectedVersion == null || expectedVersion.trim().isEmpty())
		{
			throw new IllegalArgumentException("expected version is empty");
		}

		Map<String, String> strings = matches(STRING_FIELD, json);
		Map<String, String> integers = matches(INTEGER_FIELD, json);
		Map<String, String> booleans = matches(BOOLEAN_FIELD, json);
		validateFieldNames(json);

		int schemaVersion = requiredInt(integers, "schemaVersion");
		if (schemaVersion != BuildInfo.CURRENT_SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unsupported build_info.json schema: " + schemaVersion);
		}
		String modId = requiredString(strings, "modId");
		if (!ModInfo.ID.equals(modId))
		{
			throw new IllegalArgumentException("build_info.json mod ID mismatch: " + modId);
		}
		String modVersion = requiredString(strings, "modVersion");
		if (!expectedVersion.equals(modVersion))
		{
			throw new IllegalArgumentException("build_info.json version mismatch: " + modVersion + " != " + expectedVersion);
		}
		ReleaseChannel releaseChannel = requiredEnum(strings, "releaseChannel", ReleaseChannel.class);
		if (releaseChannel != ReleaseChannel.fromVersion(modVersion))
		{
			throw new IllegalArgumentException("build_info.json release channel does not match version");
		}
		String repository = requiredString(strings, "repository");
		String upstreamRepository = requiredString(strings, "upstreamRepository");
		String branchOrChannel = requiredString(strings, "branchOrChannel");
		String commit = requiredCommit(strings, "commit", 40, 40);
		String shortCommit = requiredCommit(strings, "shortCommit", 7, 12);
		if (!commit.startsWith(shortCommit))
		{
			throw new IllegalArgumentException("build_info.json short commit does not match full commit");
		}
		String treeState = requiredString(strings, "treeState");
		if (!treeState.equals("clean") && !treeState.equals("modified"))
		{
			throw new IllegalArgumentException("Invalid build_info.json tree state: " + treeState);
		}
		String sourceTreeDigest = requiredSha256(strings, "sourceTreeDigest");
		String buildSource = requiredString(strings, "buildSource");
		String minecraftVersion = requiredString(strings, "minecraftVersion");
		String forgeVersion = requiredString(strings, "forgeVersion");
		int javaClassFileTarget = requiredInt(integers, "javaClassFileTarget");
		String packageContractVersion = requiredString(strings, "packageContractVersion");
		UpdaterPolicy updaterPolicy = requiredEnum(strings, "updaterPolicy", UpdaterPolicy.class);
		boolean reproducibleBuild = requiredBoolean(booleans, "reproducibleBuild");

		validateCommonContract(upstreamRepository, minecraftVersion, forgeVersion, javaClassFileTarget);
		validateChannelContract(
			releaseChannel, repository, treeState, buildSource, packageContractVersion, updaterPolicy, reproducibleBuild
		);
		validateNoMachineData(json);

		return new BuildInfo(
			schemaVersion,
			modId,
			modVersion,
			releaseChannel,
			repository,
			upstreamRepository,
			branchOrChannel,
			commit,
			shortCommit,
			treeState,
			sourceTreeDigest,
			buildSource,
			minecraftVersion,
			forgeVersion,
			javaClassFileTarget,
			packageContractVersion,
			updaterPolicy,
			reproducibleBuild
		);
	}

	private static void validateFieldNames(String json)
	{
		Map<String, Integer> counts = new LinkedHashMap<>();
		Matcher matcher = FIELD_NAME.matcher(json);
		while (matcher.find())
		{
			String name = matcher.group(1);
			counts.put(name, counts.getOrDefault(name, 0) + 1);
		}
		for (Map.Entry<String, Integer> entry : counts.entrySet())
		{
			if (entry.getValue() != 1)
			{
				throw new IllegalArgumentException("Duplicate build_info.json field: " + entry.getKey());
			}
			if (!EXPECTED_FIELDS.contains(entry.getKey()))
			{
				throw new IllegalArgumentException("Unknown build_info.json field: " + entry.getKey());
			}
		}
		if (!counts.keySet().equals(EXPECTED_FIELDS))
		{
			Set<String> missing = new java.util.HashSet<>(EXPECTED_FIELDS);
			missing.removeAll(counts.keySet());
			throw new IllegalArgumentException("Missing build_info.json fields: " + missing);
		}
	}

	private static void validateCommonContract(String upstreamRepository, String minecraftVersion, String forgeVersion, int javaClassFileTarget)
	{
		if (!UPSTREAM_REPOSITORY.equals(upstreamRepository))
		{
			throw new IllegalArgumentException("build_info.json upstream repository mismatch: " + upstreamRepository);
		}
		if (!"1.7.10".equals(minecraftVersion) || !"10.13.4.1614".equals(forgeVersion))
		{
			throw new IllegalArgumentException("build_info.json Minecraft/Forge contract mismatch");
		}
		if (javaClassFileTarget != 65)
		{
			throw new IllegalArgumentException("build_info.json Java class-file target mismatch: " + javaClassFileTarget);
		}
	}

	private static void validateChannelContract(
		ReleaseChannel releaseChannel,
		String repository,
		String treeState,
		String buildSource,
		String packageContractVersion,
		UpdaterPolicy updaterPolicy,
		boolean reproducibleBuild
	)
	{
		if (releaseChannel == ReleaseChannel.DEVELOPMENT)
		{
			if (WDG_REPOSITORY.equals(repository) && updaterPolicy != UpdaterPolicy.MANAGED_DISABLED)
			{
				throw new IllegalArgumentException("WDG development provenance must disable the upstream updater");
			}
			return;
		}
		if (!WDG_REPOSITORY.equals(repository))
		{
			throw new IllegalArgumentException("WDG production provenance repository mismatch: " + repository);
		}
		if (!PACKAGE_CONTRACT.equals(packageContractVersion))
		{
			throw new IllegalArgumentException("WDG package contract mismatch: " + packageContractVersion);
		}
		if (updaterPolicy != UpdaterPolicy.MANAGED_DISABLED)
		{
			throw new IllegalArgumentException("WDG production provenance must use MANAGED_DISABLED updater policy");
		}
		if (!reproducibleBuild)
		{
			throw new IllegalArgumentException("WDG production provenance must identify a reproducible build");
		}
		if ("UNCOMMITTED_VALIDATION".equals(buildSource))
		{
			if (!"modified".equals(treeState))
			{
				throw new IllegalArgumentException("Uncommitted validation provenance must identify a modified tree");
			}
		}
		else if ("CLEAN_GIT_CHECKOUT".equals(buildSource))
		{
			if (!"clean".equals(treeState))
			{
				throw new IllegalArgumentException("Final provenance must identify a clean tree");
			}
		}
		else
		{
			throw new IllegalArgumentException("Invalid WDG production build source: " + buildSource);
		}
		if (releaseChannel == ReleaseChannel.STABLE && !"clean".equals(treeState))
		{
			throw new IllegalArgumentException("Stable provenance requires a clean tree");
		}
	}

	private static void validateNoMachineData(String json)
	{
		String lower = json.toLowerCase(Locale.ROOT);
		for (String forbidden : new String[] {"unknown", "no-git-tag-set", "-dirty", "/users/", "/home/", "c:\\\\users\\\\", "hostname", "username"})
		{
			if (lower.contains(forbidden))
			{
				throw new IllegalArgumentException("Forbidden machine-specific or placeholder build provenance: " + forbidden);
			}
		}
	}

	private static Map<String, String> matches(Pattern pattern, String json)
	{
		Map<String, String> values = new LinkedHashMap<>();
		Matcher matcher = pattern.matcher(json);
		while (matcher.find())
		{
			if (values.put(matcher.group(1), matcher.group(2)) != null)
			{
				throw new IllegalArgumentException("Duplicate build_info.json field: " + matcher.group(1));
			}
		}
		return values;
	}

	private static String requiredString(Map<String, String> values, String key)
	{
		String value = values.get(key);
		if (value == null || value.trim().isEmpty())
		{
			throw new IllegalArgumentException("Missing or invalid build_info.json field: " + key);
		}
		if (value.equals("UNKNOWN") || value.equals("NO-GIT-TAG-SET"))
		{
			throw new IllegalArgumentException("Forbidden placeholder in build_info.json field: " + key);
		}
		return value;
	}

	private static int requiredInt(Map<String, String> values, String key)
	{
		String value = values.get(key);
		if (value == null)
		{
			throw new IllegalArgumentException("Missing or invalid integer build_info.json field: " + key);
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("Invalid integer build_info.json field: " + key, e);
		}
	}

	private static boolean requiredBoolean(Map<String, String> values, String key)
	{
		String value = values.get(key);
		if (value == null)
		{
			throw new IllegalArgumentException("Missing or invalid boolean build_info.json field: " + key);
		}
		return Boolean.parseBoolean(value);
	}

	private static String requiredCommit(Map<String, String> values, String key, int minimumLength, int maximumLength)
	{
		String value = requiredString(values, key);
		if (value.length() < minimumLength || value.length() > maximumLength || !value.matches("[0-9a-f]+"))
		{
			throw new IllegalArgumentException("Invalid Git commit in build_info.json field: " + key);
		}
		return value;
	}

	private static String requiredSha256(Map<String, String> values, String key)
	{
		String value = requiredString(values, key);
		if (!value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid SHA-256 in build_info.json field: " + key);
		}
		return value;
	}

	private static <T extends Enum<T>> T requiredEnum(Map<String, String> values, String key, Class<T> type)
	{
		String value = requiredString(values, key);
		try
		{
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalArgumentException("Invalid build_info.json enum field " + key + ": " + value, e);
		}
	}
}
