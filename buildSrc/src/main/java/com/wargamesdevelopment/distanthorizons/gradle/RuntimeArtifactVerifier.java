package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuntimeArtifactVerifier {

    public static final String ANGELICA_VERSION = "2.1.54";
    public static final String ANGELICA_FILENAME = "angelica-2.1.54.jar";
    public static final long ANGELICA_SIZE = 7_722_258L;
    public static final String ANGELICA_SHA256 =
        "c805cf86843cb9134f2bc9c74267b2c1e7b4237f6d505a3a6ffd92fdd86a7c3a";

    public static final String UNIMIXINS_VERSION = "0.1.23";
    public static final String UNIMIXINS_FILENAME = "+unimixins-all-1.7.10-0.1.23.jar";
    public static final long UNIMIXINS_SIZE = 5_786_196L;
    public static final String UNIMIXINS_SHA256 =
        "53b1721a9fef8e3351ece7c6b1b9598ed5290f67b1d00f312244ef603845425e";

    public static final String GTNHLIB_VERSION = "0.11.31";
    public static final String GTNHLIB_FILENAME = "gtnhlib-0.11.31.jar";
    public static final long GTNHLIB_SIZE = 1_346_274L;
    public static final String GTNHLIB_SHA256 =
        "359585849cbaf93b300b55467fc5e72ea0317a032e8e1309365e003604d54c0c";
    public static final String GTNHLIB_SOURCE_FILENAME = "GTNHLib-0.11.31.zip";
    public static final String GTNHLIB_SOURCE_SHA256 =
        "2f5f46f6459cfccb384d892e354a2c27c2cf0d4f98da2818216521ca9d845d73";
    public static final String GTNHLIB_NESTED_JAR = "fplib_deploader.jar";
    public static final long GTNHLIB_NESTED_JAR_SIZE = 78_531L;
    public static final String GTNHLIB_NESTED_JAR_SHA256 =
        "272234bfca7a9c7b75ecbcf90ad6dd0fb63dd3d7cb45011061387558d317cae4";
    public static final List<String> RUNTIME_PLATFORMS = List.of(
        "linux-aarch64",
        "linux-x86_64",
        "macos-aarch64",
        "macos-x86_64",
        "windows-aarch64",
        "windows-x86_64"
    );

    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern STRING_FIELD_PATTERN = Pattern.compile(
        "\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
    );
    private static final List<String> FORBIDDEN_CLASSIFIERS = List.of(
        "-dev.jar", "-dev-preshadow.jar", "-sources.jar", "-source.jar", "-api.jar", "-tests.jar", "-test.jar"
    );
    private static final List<String> FORBIDDEN_USER_PATHS = List.of(
        "saves/", "world/", "worlds/", "config/", "logs/", "crash-reports/", "screenshots/",
        "options.txt", "servers.dat", "launcher_accounts.json",
        "accounts.json", "usercache.json"
    );

    private RuntimeArtifactVerifier() {}

    public record ModIdentity(String modId, String version, String minecraftVersion) {}

    public record ArtifactIdentity(
        String role,
        String filename,
        long size,
        String sha256,
        List<String> modIds,
        String version,
        int baseClassMajor,
        List<Integer> multiReleaseMajors
    ) {}

    public static Map<String, Object> verifyGtnhLib(File artifact) throws IOException {
        return verifyGtnhLib(artifact, true);
    }

    static Map<String, Object> verifyGtnhLibFixture(File artifact) throws IOException {
        return verifyGtnhLib(artifact, false);
    }

    private static Map<String, Object> verifyGtnhLib(File artifact, boolean requireExactIdentity) throws IOException {
        if (requireExactIdentity) {
            requireExactBytes(artifact, "GTNHLib", GTNHLIB_SIZE, GTNHLIB_SHA256);
        } else {
            FoundationSupport.requireReadableRegularFile(artifact, "GTNHLib fixture");
        }
        validateProductionJarName(artifact.getName(), "GTNHLib");
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(artifact)) {
            validateSafeArchive(inventory);
            verifyGtnhLibNestedJar(inventory, requireExactIdentity);
            inventory.require("mcmod.info");
            inventory.require("com/gtnewhorizon/gtnhlib/GTNHLib.class");
            inventory.require("com/gtnewhorizon/gtnhlib/core/GTNHLibCore.class");
            inventory.require("com/gtnewhorizon/gtnhlib/core/GTNHLibCoreModContainer.class");
            inventory.require("META-INF/gtnhlib_at.cfg");
            inventory.require("mixins.gtnhlib.json");
            inventory.require("mixins.gtnhlib.early.json");
            inventory.require("META-INF/rfb-plugin/gtnhlib.properties");

            ModIdentity identity = requireIdentity(inventory, "gtnhlib", GTNHLIB_VERSION, "1.7.10");
            String multiRelease = inventory.manifestValue("Multi-Release");
            if (!"true".equalsIgnoreCase(multiRelease)) {
                throw new IllegalStateException("GTNHLib production JAR is not marked Multi-Release: true");
            }
            String rfbMetadata = inventory.text("META-INF/rfb-plugin/gtnhlib.properties");
            requireContains(rfbMetadata, GTNHLIB_VERSION, "GTNHLib RFB metadata version");
            String versionConsumers = new String(
                inventory.bytes("com/gtnewhorizon/gtnhlib/GTNHLib.class"),
                StandardCharsets.ISO_8859_1
            ) + new String(
                inventory.bytes("com/gtnewhorizon/gtnhlib/core/GTNHLibCoreModContainer.class"),
                StandardCharsets.ISO_8859_1
            );
            requireContains(versionConsumers, GTNHLIB_VERSION, "GTNHLib inlined Tags.VERSION");
            rejectMarkers(
                versionConsumers,
                "GTNHLib inlined Tags.VERSION",
                "NO-GIT-TAG-SET",
                "0.0.0",
                "dirty"
            );

            ClassMajors majors = inspectClassMajors(inventory);
            if (majors.baseMax() > 52) {
                throw new IllegalStateException("GTNHLib base classes exceed Java 8: major=" + majors.baseMax());
            }
            if (!majors.multiReleaseMajors().contains(61)) {
                throw new IllegalStateException(
                    "GTNHLib lacks Java 17 multi-release classes: " + majors.multiReleaseMajors()
                );
            }
            for (int major : majors.multiReleaseMajors()) {
                if (major > 61) {
                    throw new IllegalStateException("GTNHLib multi-release class exceeds Java 17: major=" + major);
                }
            }
            rejectDevelopmentContent(
                inventory,
                "GTNHLib",
                Set.of(GTNHLIB_NESTED_JAR)
            );
            return report("gtnhlib", artifact, List.of(identity.modId()), identity.version(), majors);
        }
    }

    public static Map<String, Object> verifyAngelica(File artifact) throws IOException {
        return verifyAngelica(artifact, true);
    }

    static Map<String, Object> verifyAngelicaFixture(File artifact) throws IOException {
        return verifyAngelica(artifact, false);
    }

    private static Map<String, Object> verifyAngelica(File artifact, boolean requireExactIdentity) throws IOException {
        if (requireExactIdentity) {
            requireExactBytes(artifact, "Angelica", ANGELICA_SIZE, ANGELICA_SHA256);
        } else {
            FoundationSupport.requireReadableRegularFile(artifact, "Angelica fixture");
        }
        validateProductionJarName(artifact.getName(), "Angelica");
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(artifact)) {
            validateSafeArchive(inventory);
            inventory.require("mcmod.info");
            inventory.require("com/gtnewhorizons/angelica/AngelicaMod.class");
            inventory.require("com/gtnewhorizons/angelica/loading/AngelicaTweaker.class");
            inventory.require("mixins.angelica.json");
            inventory.require("META-INF/angelica_at.cfg");
            inventory.require("META-INF/notfine_at.cfg");
            inventory.require("META-INF/archaicfix_at.cfg");
            inventory.require("META-INF/mcpatcherforge_at.cfg");

            ModIdentity identity = requireIdentity(inventory, "angelica", ANGELICA_VERSION, "1.7.10");
            List<ModIdentity> identities = readModIdentities(inventory.text("mcmod.info"));
            requireManifestValue(
                inventory,
                "FMLCorePlugin",
                "com.gtnewhorizons.angelica.loading.AngelicaTweaker"
            );
            requireManifestValue(inventory, "TweakClass", "org.spongepowered.asm.launch.MixinTweaker");
            requireManifestContains(inventory, "MixinConfigs", "mixins.angelica.json");
            requireManifestValue(inventory, "Multi-Release", "true");
            ClassMajors majors = inspectClassMajors(inventory);
            if (majors.baseMax() > 52) {
                throw new IllegalStateException("Angelica base classes exceed Java 8: major=" + majors.baseMax());
            }
            if (!majors.multiReleaseMajors().contains(61) || !majors.multiReleaseMajors().contains(65)) {
                throw new IllegalStateException(
                    "Angelica lacks its expected Java 17/21 multi-release classes: " + majors.multiReleaseMajors()
                );
            }
            rejectDevelopmentContent(inventory, "Angelica");
            return report(
                "angelica",
                artifact,
                identities.stream().map(ModIdentity::modId).toList(),
                identity.version(),
                majors
            );
        }
    }

    public static Map<String, Object> verifyUniMixins(File artifact) throws IOException {
        return verifyUniMixins(artifact, true);
    }

    static Map<String, Object> verifyUniMixinsFixture(File artifact) throws IOException {
        return verifyUniMixins(artifact, false);
    }

    private static Map<String, Object> verifyUniMixins(File artifact, boolean requireExactIdentity) throws IOException {
        if (requireExactIdentity) {
            requireExactBytes(artifact, "UniMixins All", UNIMIXINS_SIZE, UNIMIXINS_SHA256);
        } else {
            FoundationSupport.requireReadableRegularFile(artifact, "UniMixins fixture");
        }
        validateProductionJarName(artifact.getName(), "UniMixins All");
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(artifact)) {
            validateSafeArchive(inventory);
            inventory.require("mcmod.info");
            inventory.require("io/github/legacymoddingmc/unimixins/all/AllCore.class");
            inventory.require("com/gtnewhorizon/gtnhmixins/GTNHMixins.class");
            inventory.require("com/gtnewhorizon/gtnhmixins/core/GTNHMixinsCore.class");
            inventory.require("mixins.gtnhmixins.json");
            inventory.require("mixins.gtnhmixins.refmap.json");

            ModIdentity identity = requireIdentity(inventory, "unimixins", UNIMIXINS_VERSION, "1.7.10");
            requireIdentity(inventory, "gtnhmixins", null, "1.7.10");
            List<ModIdentity> identities = readModIdentities(inventory.text("mcmod.info"));
            requireManifestValue(inventory, "FMLCorePlugin", "io.github.legacymoddingmc.unimixins.all.AllCore");
            requireManifestValue(inventory, "TweakClass", "org.spongepowered.asm.launch.MixinTweaker");
            requireManifestContains(inventory, "MixinConfigs", "mixins.gtnhmixins.json");
            ClassMajors majors = inspectClassMajors(inventory);
            if (majors.baseMax() > 52) {
                throw new IllegalStateException("UniMixins base classes exceed Java 8: major=" + majors.baseMax());
            }
            if (!majors.multiReleaseMajors().isEmpty()) {
                throw new IllegalStateException("Unexpected UniMixins multi-release classes: " + majors.multiReleaseMajors());
            }
            rejectDevelopmentContent(inventory, "UniMixins");
            return report(
                "unimixins",
                artifact,
                identities.stream().map(ModIdentity::modId).toList(),
                identity.version(),
                majors
            );
        }
    }

    public static Map<String, Object> verifyRuntimeBundle(File bundle) throws IOException {
        FoundationSupport.requireReadableRegularFile(bundle, "Normalized Java runtime bundle");
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(bundle)) {
            validateSafeArchive(inventory);
            String root = singleRoot(inventory.names());
            String manifest = root + "/manifest.json";
            inventory.require(manifest);
            String manifestText = inventory.text(manifest);
            requireContains(manifestText, "\"javaRuntimeVersion\": \"21.0.11+10-LTS\"", "runtime version");
            for (String platform : RUNTIME_PLATFORMS) {
                requireContains(manifestText, "\"id\": \"" + platform + "\"", "runtime platform " + platform);
                String suffix = platform.startsWith("windows-") ? ".zip" : ".tar.gz";
                inventory.require(root + "/runtimes/" + platform + suffix);
            }
            long runtimeMembers = inventory.names().stream()
                .filter(name -> name.startsWith(root + "/runtimes/"))
                .filter(name -> name.endsWith(".zip") || name.endsWith(".tar.gz"))
                .count();
            if (runtimeMembers != 6) {
                throw new IllegalStateException("Normalized runtime bundle must contain exactly six runtime archives");
            }
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("artifactName", bundle.getName());
            report.put("artifactSize", Files.size(bundle.toPath()));
            report.put("artifactSha256", FoundationSupport.sha256(bundle.toPath()));
            report.put("rootDirectory", root);
            report.put("memberCount", inventory.names().size());
            report.put("runtimeVersion", "21.0.11+10-LTS");
            report.put("supportedPlatforms", String.join(",", RUNTIME_PLATFORMS));
            report.put("verified", true);
            return report;
        }
    }

    public static Map<String, Object> verifyBundledClientOverlay(
        File overlay,
        File expectedLwjgl3ify,
        File expectedRuntimeBundle
    ) throws IOException {
        FoundationSupport.requireReadableRegularFile(overlay, "lwjgl3ify bundled-client package");
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(overlay)) {
            validateSafeArchive(inventory);
            String root = singleRoot(inventory.names());
            List<String> jarMembers = inventory.names().stream()
                .filter(name -> name.startsWith(root + "/mods/") && name.endsWith(".jar"))
                .toList();
            if (jarMembers.size() != 1) {
                throw new IllegalStateException("Bundled-client overlay must contain exactly one mod JAR: " + jarMembers);
            }
            List<String> runtimeMembers = inventory.names().stream()
                .filter(name -> name.startsWith(root + "/lwjgl3ify/runtime/") && name.endsWith(".zip"))
                .toList();
            if (runtimeMembers.size() != 1) {
                throw new IllegalStateException(
                    "Bundled-client overlay must contain exactly one normalized runtime bundle: " + runtimeMembers
                );
            }
            requireBytesEqual(inventory, jarMembers.get(0), expectedLwjgl3ify, "lwjgl3ify overlay mod");
            requireBytesEqual(
                inventory,
                runtimeMembers.get(0),
                expectedRuntimeBundle,
                "lwjgl3ify overlay runtime bundle"
            );
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("artifactName", overlay.getName());
            report.put("artifactSize", Files.size(overlay.toPath()));
            report.put("artifactSha256", FoundationSupport.sha256(overlay.toPath()));
            report.put("rootDirectory", root);
            report.put("memberCount", inventory.names().size());
            report.put("modMember", jarMembers.get(0));
            report.put("runtimeMember", runtimeMembers.get(0));
            report.put("verified", true);
            return report;
        }
    }

    public static List<ModIdentity> readModIdentities(String mcmodInfo) {
        List<ModIdentity> identities = new ArrayList<>();
        Matcher objectMatcher = OBJECT_PATTERN.matcher(mcmodInfo);
        while (objectMatcher.find()) {
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher fieldMatcher = STRING_FIELD_PATTERN.matcher(objectMatcher.group(1));
            while (fieldMatcher.find()) {
                fields.putIfAbsent(fieldMatcher.group(1), fieldMatcher.group(2));
            }
            String modId = fields.get("modid");
            if (modId != null && !modId.isBlank()) {
                identities.add(new ModIdentity(modId, fields.getOrDefault("version", ""), fields.getOrDefault("mcversion", "")));
            }
        }
        if (identities.isEmpty()) {
            throw new IllegalStateException("mcmod.info does not contain any mod identities");
        }
        return List.copyOf(identities);
    }

    public static String singleRoot(List<String> names) {
        Set<String> roots = new LinkedHashSet<>();
        for (String name : names) {
            if (name.isBlank()) {
                continue;
            }
            int slash = name.indexOf('/');
            if (slash <= 0) {
                throw new IllegalStateException("Archive member is outside a top-level directory: " + name);
            }
            roots.add(name.substring(0, slash));
        }
        if (roots.size() != 1) {
            throw new IllegalStateException("Archive must contain exactly one top-level directory: " + roots);
        }
        return roots.iterator().next();
    }

    public static void validateSafeArchive(FoundationSupport.ArchiveInventory inventory) {
        for (String name : inventory.names()) {
            String normalized = name.replace('\\', '/');
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (!name.equals(normalized)
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("//")) {
                throw new IllegalStateException("Unsafe archive member path: " + name);
            }
            for (String segment : normalized.split("/")) {
                if (segment.equals("..") || segment.equals(".")) {
                    throw new IllegalStateException("Unsafe archive traversal member: " + name);
                }
            }
            for (String forbidden : FORBIDDEN_USER_PATHS) {
                if (lower.startsWith(forbidden)) {
                    throw new IllegalStateException("Artifact contains forbidden user/runtime data: " + name);
                }
            }
        }
    }

    public static void validateProductionJarName(String name, String label) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar")) {
            throw new IllegalStateException(label + " input is not a JAR: " + name);
        }
        for (String classifier : FORBIDDEN_CLASSIFIERS) {
            if (lower.endsWith(classifier)) {
                throw new IllegalStateException(label + " development/classified artifact rejected: " + name);
            }
        }
    }

    private static ModIdentity requireIdentity(
        FoundationSupport.ArchiveInventory inventory,
        String modId,
        String expectedVersion,
        String expectedMinecraftVersion
    ) throws IOException {
        return requireIdentity(inventory.text("mcmod.info"), modId, expectedVersion, expectedMinecraftVersion);
    }

    static ModIdentity requireIdentity(
        String mcmodInfo,
        String modId,
        String expectedVersion,
        String expectedMinecraftVersion
    ) {
        for (ModIdentity identity : readModIdentities(mcmodInfo)) {
            if (!modId.equals(identity.modId())) {
                continue;
            }
            if (expectedVersion != null && !expectedVersion.equals(identity.version())) {
                throw new IllegalStateException(
                    modId + " version mismatch: expected=" + expectedVersion + ", actual=" + identity.version()
                );
            }
            if (expectedMinecraftVersion != null && !expectedMinecraftVersion.equals(identity.minecraftVersion())) {
                throw new IllegalStateException(
                    modId + " Minecraft version mismatch: expected=" + expectedMinecraftVersion
                        + ", actual=" + identity.minecraftVersion()
                );
            }
            return identity;
        }
        throw new IllegalStateException("Required mod identity is absent: " + modId);
    }

    private static void requireExactBytes(File artifact, String label, long expectedSize, String expectedSha256)
        throws IOException {
        FoundationSupport.requireReadableRegularFile(artifact, label);
        long actualSize = Files.size(artifact.toPath());
        if (actualSize != expectedSize) {
            throw new IllegalStateException(
                label + " byte-size mismatch: expected=" + expectedSize + ", actual=" + actualSize
            );
        }
        String actualSha256 = FoundationSupport.sha256(artifact.toPath());
        if (!expectedSha256.equals(actualSha256)) {
            throw new IllegalStateException(
                label + " SHA-256 mismatch: expected=" + expectedSha256 + ", actual=" + actualSha256
            );
        }
    }

    private static void requireBytesEqual(
        FoundationSupport.ArchiveInventory inventory,
        String member,
        File expected,
        String label
    ) throws IOException {
        if (!inventory.contentEquals(member, expected.toPath())) {
            throw new IllegalStateException(label + " bytes do not match the verified external artifact");
        }
    }

    private static void requireManifestValue(
        FoundationSupport.ArchiveInventory inventory,
        String key,
        String expected
    ) throws IOException {
        String actual = inventory.manifestValue(key);
        if (!expected.equalsIgnoreCase(actual == null ? "" : actual.trim())) {
            throw new IllegalStateException(
                "Manifest " + key + " mismatch: expected=" + expected + ", actual=" + actual
            );
        }
    }

    private static void requireManifestContains(
        FoundationSupport.ArchiveInventory inventory,
        String key,
        String expectedPart
    ) throws IOException {
        String actual = inventory.manifestValue(key);
        if (actual == null || !actual.contains(expectedPart)) {
            throw new IllegalStateException("Manifest " + key + " lacks " + expectedPart + ": " + actual);
        }
    }

    static void verifyExpectedNestedJar(
        FoundationSupport.ArchiveInventory inventory,
        String expectedName,
        long expectedSize,
        String expectedSha256,
        boolean requireExactBytes
    ) throws IOException {
        List<String> nestedJars = inventory.names().stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".jar"))
            .toList();

        if (!nestedJars.equals(List.of(expectedName))) {
            throw new IllegalStateException(
                "Artifact nested-JAR inventory mismatch: expected="
                    + List.of(expectedName)
                    + ", actual="
                    + nestedJars
            );
        }

        byte[] nestedBytes = inventory.bytes(expectedName);
        if (nestedBytes.length == 0) {
            throw new IllegalStateException("Expected nested JAR is empty: " + expectedName);
        }

        if (requireExactBytes && nestedBytes.length != expectedSize) {
            throw new IllegalStateException(
                "Nested JAR byte-size mismatch for "
                    + expectedName
                    + ": expected="
                    + expectedSize
                    + ", actual="
                    + nestedBytes.length
            );
        }

        if (requireExactBytes) {
            String actualSha256 = sha256(nestedBytes);
            if (!expectedSha256.equals(actualSha256)) {
                throw new IllegalStateException(
                    "Nested JAR SHA-256 mismatch for "
                        + expectedName
                        + ": expected="
                        + expectedSha256
                        + ", actual="
                        + actualSha256
                );
            }
        }
    }

    private static void verifyGtnhLibNestedJar(
        FoundationSupport.ArchiveInventory inventory,
        boolean requireExactBytes
    ) throws IOException {
        verifyExpectedNestedJar(
            inventory,
            GTNHLIB_NESTED_JAR,
            GTNHLIB_NESTED_JAR_SIZE,
            GTNHLIB_NESTED_JAR_SHA256,
            requireExactBytes
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hexadecimal.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void rejectDevelopmentContent(
        FoundationSupport.ArchiveInventory inventory,
        String label
    ) {
        rejectDevelopmentContent(inventory, label, Set.of());
    }

    private static void rejectDevelopmentContent(
        FoundationSupport.ArchiveInventory inventory,
        String label,
        Set<String> permittedNestedJars
    ) {
        for (String name : inventory.names()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".java")
                || lower.contains("/src/test/")
                || lower.contains("/testclasses/")
                || lower.endsWith("build.gradle")
                || lower.endsWith("build.gradle.kts")
                || lower.endsWith("gradle.properties")
                || lower.endsWith("settings.gradle")
                || lower.endsWith("settings.gradle.kts")) {
                throw new IllegalStateException(label + " contains development-only content: " + name);
            }
            if (lower.endsWith(".jar") && !permittedNestedJars.contains(name)) {
                throw new IllegalStateException(label + " contains an unexpected nested JAR: " + name);
            }
        }
    }

    private static void rejectMarkers(String text, String label, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                throw new IllegalStateException(label + " contains forbidden marker: " + marker);
            }
        }
    }

    private static void requireContains(String text, String marker, String label) {
        if (!text.contains(marker)) {
            throw new IllegalStateException(label + " is missing required marker: " + marker);
        }
    }

    private static Map<String, Object> report(
        String role,
        File artifact,
        List<String> modIds,
        String version,
        ClassMajors majors
    ) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("role", role);
        report.put("artifactName", artifact.getName());
        report.put("artifactSize", Files.size(artifact.toPath()));
        report.put("artifactSha256", FoundationSupport.sha256(artifact.toPath()));
        report.put("modIds", String.join(",", modIds));
        report.put("version", version);
        report.put("baseClassMajor", majors.baseMax());
        report.put("multiReleaseMajors", majors.multiReleaseMajors().toString());
        report.put("production", true);
        return report;
    }

    private static ClassMajors inspectClassMajors(FoundationSupport.ArchiveInventory inventory) throws IOException {
        int baseMax = 0;
        Set<Integer> multiRelease = new LinkedHashSet<>();
        for (String name : inventory.names()) {
            if (!name.endsWith(".class")) {
                continue;
            }
            int major = FoundationSupport.classMajor(inventory.bytes(name), name);
            if (name.startsWith("META-INF/versions/")) {
                multiRelease.add(major);
            } else {
                baseMax = Math.max(baseMax, major);
            }
        }
        if (baseMax == 0) {
            throw new IllegalStateException("Artifact contains no base Java classes");
        }
        return new ClassMajors(baseMax, List.copyOf(multiRelease));
    }

    private record ClassMajors(int baseMax, List<Integer> multiReleaseMajors) {}
}
