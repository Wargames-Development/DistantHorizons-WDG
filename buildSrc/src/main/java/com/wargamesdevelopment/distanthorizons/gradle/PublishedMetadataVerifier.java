package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PublishedMetadataVerifier {

    private static final List<String> REQUIRED = List.of("lwjgl3ify", "gtnhlib", "unimixins");
    private static final List<String> FORBIDDEN = List.of(
        "hodgepodge",
        "notenoughitems",
        "angelica",
        "gt5-unofficial",
        "rple-mc1.7.10"
    );
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
        "<dependency>(.*?)</dependency>", Pattern.DOTALL
    );
    private static final Pattern PROJECT_VERSION_PATTERN = Pattern.compile(
        "<version>([^<]+)</version>"
    );
    private static final Pattern MODULE_COMPONENT_VERSION_PATTERN = Pattern.compile(
        "\"component\"\\s*:\\s*\\{.*?\"version\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL
    );

    private PublishedMetadataVerifier() {}

    public static Map<String, Object> verify(File pomFile, File moduleFile, String expectedVersion) throws IOException {
        FoundationSupport.requireReadableRegularFile(pomFile, "Generated Maven POM");
        FoundationSupport.requireReadableRegularFile(moduleFile, "Generated Gradle module metadata");
        expectedVersion = FoundationSupport.validateVersion(expectedVersion);
        String pom = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
        String module = Files.readString(moduleFile.toPath(), StandardCharsets.UTF_8);
        String pomLower = pom.toLowerCase(Locale.ROOT);
        String moduleLower = module.toLowerCase(Locale.ROOT);
        String combined = pomLower + "\n" + moduleLower;

        FoundationSupport.requireVersionMatch(
            expectedVersion, firstMatch(PROJECT_VERSION_PATTERN, pom, "Generated Maven POM project version"), "Generated Maven POM"
        );
        FoundationSupport.requireVersionMatch(
            expectedVersion,
            firstMatch(MODULE_COMPONENT_VERSION_PATTERN, module, "Generated Gradle module component version"),
            "Generated Gradle module metadata"
        );
        for (String required : REQUIRED) {
            if (!pomLower.contains(required)) {
                throw new IllegalStateException("Required dependency is missing from the generated POM: " + required);
            }
            if (!moduleLower.contains(required)) {
                throw new IllegalStateException("Required dependency is missing from Gradle module metadata: " + required);
            }
        }
        for (String forbidden : FORBIDDEN) {
            if (combined.contains(forbidden)) {
                throw new IllegalStateException("Optional/development dependency was published as mandatory: " + forbidden);
            }
        }
        for (String localMarker : List.of(
            "wdglwjgl3ify", "/users/", "/home/", "file:/", "\\users\\", "c:\\users\\"
        )) {
            if (combined.contains(localMarker)) {
                throw new IllegalStateException("Generated dependency metadata leaks a local override/path: " + localMarker);
            }
        }

        List<String> pomDependencies = new ArrayList<>();
        Matcher matcher = DEPENDENCY_PATTERN.matcher(pom);
        while (matcher.find()) {
            String dependency = matcher.group(1);
            String group = xmlValue(dependency, "groupId");
            String artifact = xmlValue(dependency, "artifactId");
            String version = xmlValue(dependency, "version");
            String scope = optionalXmlValue(dependency, "scope", "compile");
            String optional = optionalXmlValue(dependency, "optional", "false");
            String coordinate = group + ":" + artifact + ":" + version + ":" + scope + ":optional=" + optional;
            pomDependencies.add(coordinate);
            String lowerArtifact = artifact.toLowerCase(Locale.ROOT);
            if (REQUIRED.stream().anyMatch(lowerArtifact::contains)
                && ("test".equalsIgnoreCase(scope) || "true".equalsIgnoreCase(optional))) {
                throw new IllegalStateException("Required dependency has non-runtime publication metadata: " + coordinate);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("pomPath", pomFile.getAbsoluteFile().toPath().normalize());
        report.put("moduleMetadataPath", moduleFile.getAbsoluteFile().toPath().normalize());
        report.put("metadataVersion", expectedVersion);
        report.put("pomDependencies", String.join(",", pomDependencies));
        report.put("requiredDependencies", String.join(",", REQUIRED));
        report.put("optionalIntegrationsMandatory", false);
        report.put("localOverrideLeaked", false);
        return report;
    }

    private static String firstMatch(Pattern pattern, String text, String label) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException(label + " is missing");
        }
        return matcher.group(1).trim();
    }

    private static String xmlValue(String xml, String element) {
        String value = optionalXmlValue(xml, element, null);
        if (value == null) {
            throw new IllegalStateException("Generated POM dependency is missing " + element + ": " + xml.trim());
        }
        return value;
    }

    private static String optionalXmlValue(String xml, String element, String defaultValue) {
        Matcher matcher = Pattern.compile("<" + element + ">([^<]+)</" + element + ">").matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : defaultValue;
    }
}
