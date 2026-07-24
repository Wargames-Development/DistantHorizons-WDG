package com.wargamesdevelopment.distanthorizons.gradle;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildInfoArtifactVerifier {

    private static final Pattern STRING_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern INTEGER_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*([0-9]+)");
    private static final Pattern BOOLEAN_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(true|false)");

    private BuildInfoArtifactVerifier() {}

    public record Result(
        String commit,
        String branch,
        String treeState,
        String sourceTreeDigest,
        String buildSource,
        String releaseChannel,
        String updaterPolicy,
        Map<String, Object> report
    ) {}

    public static Result verify(
        String json,
        String expectedVersion,
        String expectedMode,
        String expectedCommit
    ) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("build_info.json is empty");
        }
        Map<String, String> strings = new LinkedHashMap<>();
        Matcher stringMatcher = STRING_FIELD.matcher(json);
        while (stringMatcher.find()) {
            if (strings.put(stringMatcher.group(1), stringMatcher.group(2)) != null) {
                throw new IllegalStateException("Duplicate build_info.json field: " + stringMatcher.group(1));
            }
        }
        Map<String, String> integers = matches(INTEGER_FIELD, json);
        Map<String, String> booleans = matches(BOOLEAN_FIELD, json);

        require(integers, "schemaVersion", "1");
        require(strings, "modId", FoundationSupport.MOD_ID);
        require(strings, "modVersion", expectedVersion);
        require(strings, "releaseChannel", "RELEASE_CANDIDATE");
        require(strings, "repository", ProvenanceSupport.REPOSITORY);
        require(strings, "upstreamRepository", ProvenanceSupport.UPSTREAM_REPOSITORY);
        String branch = required(strings, "branchOrChannel");
        String commit = required(strings, "commit");
        String shortCommit = required(strings, "shortCommit");
        String treeState = required(strings, "treeState");
        String digest = required(strings, "sourceTreeDigest");
        String buildSource = required(strings, "buildSource");
        require(strings, "minecraftVersion", "1.7.10");
        require(strings, "forgeVersion", "10.13.4.1614");
        require(integers, "javaClassFileTarget", Integer.toString(FoundationSupport.JAVA_21_CLASS_MAJOR));
        require(strings, "packageContractVersion", ProvenanceSupport.PACKAGE_CONTRACT);
        require(strings, "updaterPolicy", ProvenanceSupport.UPDATER_POLICY);
        require(booleans, "reproducibleBuild", "true");

        if (!commit.matches("[0-9a-f]{40}")) {
            throw new IllegalStateException("Invalid full Git commit in build_info.json: " + commit);
        }
        if (!shortCommit.matches("[0-9a-f]{7,12}") || !commit.startsWith(shortCommit)) {
            throw new IllegalStateException("Invalid short Git commit in build_info.json: " + shortCommit);
        }
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Invalid source tree digest in build_info.json");
        }
        if (!branch.equals(ProvenanceSupport.EXPECTED_BRANCH)) {
            throw new IllegalStateException("build_info.json branch/channel mismatch: " + branch);
        }

        String mode = expectedMode.toUpperCase(Locale.ROOT);
        if (mode.equals("VALIDATION")) {
            if (!commit.equals(ProvenanceSupport.BASE_COMMIT)) {
                throw new IllegalStateException("Validation build_info.json commit mismatch: " + commit);
            }
            if (!treeState.equals("modified") || !buildSource.equals(ProvenanceSupport.VALIDATION_SOURCE)) {
                throw new IllegalStateException("Validation provenance is not clearly marked UNCOMMITTED_VALIDATION");
            }
        } else if (mode.equals("FINAL")) {
            if (expectedCommit == null || !expectedCommit.matches("[0-9a-f]{40}")) {
                throw new IllegalStateException("Final production verification requires an exact expected commit");
            }
            if (!commit.equals(expectedCommit)) {
                throw new IllegalStateException("Final build_info.json commit mismatch: " + commit + " != " + expectedCommit);
            }
            if (!treeState.equals("clean") || !buildSource.equals(ProvenanceSupport.FINAL_SOURCE)) {
                throw new IllegalStateException("Final provenance must identify a clean Git checkout");
            }
        } else {
            throw new IllegalStateException("Unsupported provenance mode: " + expectedMode);
        }

        String lower = json.toLowerCase(Locale.ROOT);
        for (String forbidden : java.util.List.of(
            "unknown", "no-git-tag-set", "-dirty", "/users/", "/home/", "c:\\\\users\\", "hostname", "username"
        )) {
            if (lower.contains(forbidden)) {
                throw new IllegalStateException("Forbidden machine-specific or placeholder provenance token: " + forbidden);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("buildInfoSchema", 1);
        report.put("buildInfoCommit", commit);
        report.put("buildInfoBranch", branch);
        report.put("buildInfoTreeState", treeState);
        report.put("buildInfoSourceTreeDigest", digest);
        report.put("buildInfoBuildSource", buildSource);
        report.put("buildInfoReleaseChannel", "RELEASE_CANDIDATE");
        report.put("buildInfoUpdaterPolicy", "MANAGED_DISABLED");
        report.put("buildInfoValid", true);
        return new Result(commit, branch, treeState, digest, buildSource, "RELEASE_CANDIDATE", "MANAGED_DISABLED", report);
    }

    private static Map<String, String> matches(Pattern pattern, String json) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            if (values.put(matcher.group(1), matcher.group(2)) != null) {
                throw new IllegalStateException("Duplicate build_info.json field: " + matcher.group(1));
            }
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing build_info.json field: " + key);
        }
        return value;
    }

    private static void require(Map<String, String> values, String key, String expected) {
        String actual = required(values, key);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("build_info.json field mismatch for " + key + ": " + actual + " != " + expected);
        }
    }
}
