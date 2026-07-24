package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lwjgl3ifyCompatibilityVerifier {

    public static final String EXPECTED_FILENAME = "lwjgl3ify-3.0.28-master.5+d7e60f5a0d.jar";
    public static final long EXPECTED_SIZE = 207_893_285L;
    public static final String EXPECTED_SHA256 =
        "ef1ec515dc56fac04c6c9791d4d39b62d3b954b441070cffacc0a8516b9136ca";
    public static final String EXPECTED_VERSION = "3.0.28-master.5+d7e60f5a0d";
    public static final String EXPECTED_COMMIT = "d7e60f5a0dea4aa348e3c06b8f0a87c171522a37";
    public static final String PRODUCTION_IDENTITY =
        "change-005-verified-embedded-java-runtime-distribution";
    public static final String RUNTIME_DISTRIBUTION_MODE = "EMBEDDED_PRIMARY_RUNTIMES";
    public static final String JAVA_RUNTIME_VERSION = "21.0.11+10-LTS";

    public static final String RUNTIME_DISTRIBUTION_MANIFEST =
        "META-INF/lwjgl3ify-wdg/runtime-distribution.json";
    private static final String RUNTIME_MANIFEST =
        "me/eigenraven/lwjgl3ify/relauncher/runtime/java21-runtime-manifest.json";
    private static final String EMBEDDED_RUNTIME_PREFIX =
        "me/eigenraven/lwjgl3ify/relauncher/runtime/embedded/runtimes/";
    private static final String REFMAP = "mixins.lwjgl3ify.refmap.json";
    private static final String MIXIN_CONFIG = "mixins.lwjgl3ify.json";

    public record RuntimeArchive(
        String platformId,
        String path,
        long size,
        String sha256
    ) {}

    public record OptionalRuntimeExtension(
        String platformId,
        String filename,
        long size,
        String sha256
    ) {}

    public static final List<RuntimeArchive> PRIMARY_RUNTIMES = List.of(
        new RuntimeArchive(
            "linux-x86_64",
            EMBEDDED_RUNTIME_PREFIX + "linux-x86_64.tar.gz",
            52_099_793L,
            "e5038aae3ca9ff670bc696496b0728dbd23d280026bad30291cb919221ecfdcb"
        ),
        new RuntimeArchive(
            "macos-aarch64",
            EMBEDDED_RUNTIME_PREFIX + "macos-aarch64.tar.gz",
            48_149_317L,
            "4b7a8cd23102c251c8b8be42a9a5f1263fb337cf1037f6f64b25f3070efe4b76"
        ),
        new RuntimeArchive(
            "macos-x86_64",
            EMBEDDED_RUNTIME_PREFIX + "macos-x86_64.tar.gz",
            42_129_623L,
            "b341fb8ed5b70d49066b98176bc98e30f55082192403deb60e0cd5948b6e7923"
        ),
        new RuntimeArchive(
            "windows-x86_64",
            EMBEDDED_RUNTIME_PREFIX + "windows-x86_64.zip",
            49_005_708L,
            "be26677aaa20b39a62edcaab4c8857a8b76673b0f45abc0b6143b142b62717e4"
        )
    );

    public static final List<OptionalRuntimeExtension> OPTIONAL_EXTENSIONS = List.of(
        new OptionalRuntimeExtension(
            "linux-aarch64",
            "lwjgl3ify-wdg-java21-linux-aarch64.tar.gz",
            51_184_429L,
            "fa23d9d9945053e67bcc7638410eabf1e17a7672c7c95a24f70cd08b8407d36e"
        ),
        new OptionalRuntimeExtension(
            "windows-aarch64",
            "lwjgl3ify-wdg-java21-windows-aarch64.zip",
            40_076_904L,
            "22e2c2b83a7dc5653c938c9a49d87ad52a1faa38f7f3d80a96ceb0795ab99637"
        )
    );

    private static final List<String> REQUIRED_MEMBERS = List.of(
        "mcmod.info",
        "me/eigenraven/lwjgl3ify/relauncher/Lwjgl3ifyRelauncherTweaker.class",
        "me/eigenraven/lwjgl3ify/relauncher/Relauncher.class",
        "me/eigenraven/lwjgl3ify/relauncher/ChildProcessSupervisor.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/AutomaticRuntimeCoordinator.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/EmbeddedRuntimeArchiveProvider.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeInstaller.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeArchiveExtractor.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeManifest.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/JavaLaunchSelector.class",
        RUNTIME_DISTRIBUTION_MANIFEST,
        RUNTIME_MANIFEST,
        MIXIN_CONFIG,
        REFMAP,
        "META-INF/rfb-plugin/lwjgl3ify.properties",
        "me/eigenraven/lwjgl3ify/relauncher/forgePatches.zip",
        "me/eigenraven/lwjgl3ify/relauncher/version.json",
        "META-INF/MANIFEST.MF"
    );
    private static final List<String> JAVA_8_BOUNDARY_CLASSES = List.of(
        "me/eigenraven/lwjgl3ify/relauncher/Lwjgl3ifyRelauncherTweaker.class",
        "me/eigenraven/lwjgl3ify/relauncher/Relauncher.class",
        "me/eigenraven/lwjgl3ify/relauncher/ChildProcessSupervisor.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/AutomaticRuntimeCoordinator.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/EmbeddedRuntimeArchiveProvider.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeInstaller.class"
    );

    private Lwjgl3ifyCompatibilityVerifier() {}

    public static Map<String, Object> verify(File artifact) throws IOException {
        return verifyInternal(artifact, true);
    }

    static Map<String, Object> verifyFixture(File artifact) throws IOException {
        return verifyInternal(artifact, false);
    }

    private static Map<String, Object> verifyInternal(File artifact, boolean exactProductionIdentity)
        throws IOException {
        FoundationSupport.requireReadableRegularFile(artifact, "Production lwjgl3ify-wdg JAR");
        validateCandidateName(artifact.getName());
        if (exactProductionIdentity) {
            requireExactOuterIdentity(artifact);
        }

        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(artifact)) {
            for (String required : REQUIRED_MEMBERS) {
                inventory.require(required);
            }
            String mcmod = inventory.text("mcmod.info");
            String modId = FoundationSupport.extractJsonString(mcmod, "modid");
            if (!"lwjgl3ify".equals(modId)) {
                throw new IllegalStateException("lwjgl3ify public mod identity changed: " + modId);
            }
            String version = FoundationSupport.extractJsonString(mcmod, "version");
            FoundationSupport.validateVersion(version);
            if (exactProductionIdentity && !EXPECTED_VERSION.equals(version)) {
                throw new IllegalStateException(
                    "lwjgl3ify version mismatch: expected=" + EXPECTED_VERSION + ", actual=" + version
                );
            }

            Manifest manifest = inventory.manifest();
            String implementationVersion = manifest.getMainAttributes().getValue("Implementation-Version");
            if (implementationVersion == null || implementationVersion.isBlank()) {
                implementationVersion = version;
            }
            if (!implementationVersion.equals(version)) {
                throw new IllegalStateException(
                    "lwjgl3ify implementation/version metadata mismatch: implementation="
                        + implementationVersion + ", mcmod=" + version
                );
            }

            verifyProductionMixin(inventory);
            verifyCanonicalRuntimeManifest(inventory);
            Map<String, Object> distribution = verifyEmbeddedRuntimeDistribution(
                inventory,
                exactProductionIdentity
            );
            rejectForbiddenPayloads(inventory);

            int maxEarlyMajor = 0;
            for (String name : JAVA_8_BOUNDARY_CLASSES) {
                int major = FoundationSupport.classMajor(inventory.bytes(name), name);
                if (major > 52) {
                    throw new IllegalStateException(
                        "Early lwjgl3ify relaunch class is not Java 8 compatible: " + name + " major=" + major
                    );
                }
                maxEarlyMajor = Math.max(maxEarlyMajor, major);
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("artifactPath", artifact.getAbsoluteFile().toPath().normalize());
            report.put("artifactName", artifact.getName());
            report.put("artifactSize", Files.size(artifact.toPath()));
            report.put("artifactSha256", FoundationSupport.sha256(artifact.toPath()));
            report.put("version", version);
            report.put("implementationVersion", implementationVersion);
            report.put("publicModId", modId);
            report.put("mixinRefmap", REFMAP);
            report.put("mixinRefmapSha256", FoundationSupport.sha256(inventory.bytes(REFMAP)));
            report.put("earlyRelaunchClassMajorMax", maxEarlyMajor);
            report.put("pinnedCommit", EXPECTED_COMMIT);
            report.put("productionIdentity", PRODUCTION_IDENTITY);
            report.putAll(distribution);
            report.put("compatible", true);
            return report;
        }
    }

    private static void requireExactOuterIdentity(File artifact) throws IOException {
        if (!EXPECTED_FILENAME.equals(artifact.getName())) {
            throw new IllegalStateException(
                "Wrong runtime-bearing lwjgl3ify filename: expected=" + EXPECTED_FILENAME
                    + ", actual=" + artifact.getName()
            );
        }
        long actualSize = Files.size(artifact.toPath());
        if (actualSize != EXPECTED_SIZE) {
            throw new IllegalStateException(
                "Wrong runtime-bearing lwjgl3ify size: expected=" + EXPECTED_SIZE + ", actual=" + actualSize
            );
        }
        String actualHash = FoundationSupport.sha256(artifact.toPath());
        if (!EXPECTED_SHA256.equals(actualHash)) {
            throw new IllegalStateException(
                "Wrong runtime-bearing lwjgl3ify SHA-256: expected=" + EXPECTED_SHA256
                    + ", actual=" + actualHash
            );
        }
    }

    private static void verifyProductionMixin(FoundationSupport.ArchiveInventory inventory) throws IOException {
        String mixinConfig = inventory.text(MIXIN_CONFIG);
        if (!mixinConfig.contains(REFMAP)) {
            throw new IllegalStateException("lwjgl3ify production Mixin config does not reference " + REFMAP);
        }
        String refmap = inventory.text(REFMAP);
        String displayMixinOwner = "me.eigenraven.lwjgl3ify.mixins.early.game.MixinMinecraft_Display";
        String slashDisplayMixinOwner = displayMixinOwner.replace('.', '/');
        if (refmap.trim().length() < 32
            || !refmap.contains("\"mappings\"")
            || (!refmap.contains(displayMixinOwner) && !refmap.contains(slashDisplayMixinOwner))) {
            throw new IllegalStateException("lwjgl3ify production refmap is structurally invalid");
        }
    }

    private static void verifyCanonicalRuntimeManifest(FoundationSupport.ArchiveInventory inventory)
        throws IOException {
        String runtimeManifest = inventory.text(RUNTIME_MANIFEST).toLowerCase(Locale.ROOT);
        if (!runtimeManifest.contains("temurin")
            || !runtimeManifest.contains("sha256")
            || !runtimeManifest.contains("21.0.11+10-lts")) {
            throw new IllegalStateException("Canonical Java runtime manifest is structurally invalid");
        }
    }

    private static Map<String, Object> verifyEmbeddedRuntimeDistribution(
        FoundationSupport.ArchiveInventory inventory,
        boolean requirePinnedProductionBytes
    ) throws IOException {
        String manifest = inventory.text(RUNTIME_DISTRIBUTION_MANIFEST);
        requireJsonString(manifest, "artifactType", "lwjgl3ify-wdg-runtime-bundled");
        requireJsonString(manifest, "distribution", "Temurin");
        requireJsonString(manifest, "javaRuntimeVersion", JAVA_RUNTIME_VERSION);

        List<Map<String, String>> embeddedRecords = parseObjectArray(manifest, "embeddedPlatforms");
        if (embeddedRecords.size() != PRIMARY_RUNTIMES.size()) {
            throw new IllegalStateException(
                "Embedded primary runtime count mismatch: expected=" + PRIMARY_RUNTIMES.size()
                    + ", actual=" + embeddedRecords.size()
            );
        }
        Set<String> actualEmbeddedPaths = new LinkedHashSet<>();
        Map<String, Map<String, String>> recordsById = new LinkedHashMap<>();
        for (Map<String, String> record : embeddedRecords) {
            String id = record.get("id");
            String path = record.get("packagedResource");
            if (id == null || path == null || recordsById.put(id, record) != null) {
                throw new IllegalStateException("Malformed or duplicate embedded runtime distribution record: " + record);
            }
            actualEmbeddedPaths.add(path);
        }

        Set<String> archiveRuntimePaths = new LinkedHashSet<>();
        for (String name : inventory.names()) {
            if (name.startsWith(EMBEDDED_RUNTIME_PREFIX) && !name.endsWith("/")) {
                archiveRuntimePaths.add(name);
            }
        }
        Set<String> expectedRuntimePaths = new LinkedHashSet<>();
        for (RuntimeArchive expected : PRIMARY_RUNTIMES) {
            expectedRuntimePaths.add(expected.path());
        }
        if (!archiveRuntimePaths.equals(expectedRuntimePaths)) {
            throw new IllegalStateException(
                "Embedded primary runtime membership mismatch: expected=" + expectedRuntimePaths
                    + ", actual=" + archiveRuntimePaths
            );
        }
        if (!actualEmbeddedPaths.equals(expectedRuntimePaths)) {
            throw new IllegalStateException(
                "Runtime-distribution manifest membership mismatch: expected=" + expectedRuntimePaths
                    + ", actual=" + actualEmbeddedPaths
            );
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runtimeDistributionMode", RUNTIME_DISTRIBUTION_MODE);
        report.put("embeddedRuntimeCount", PRIMARY_RUNTIMES.size());
        report.put("javaRuntimeVersion", JAVA_RUNTIME_VERSION);
        report.put("embeddedRuntimePlatforms", PRIMARY_RUNTIMES.stream().map(RuntimeArchive::platformId).toList());
        for (RuntimeArchive expected : PRIMARY_RUNTIMES) {
            Map<String, String> record = recordsById.get(expected.platformId());
            if (record == null) {
                throw new IllegalStateException("Missing embedded runtime record for " + expected.platformId());
            }
            if (!expected.path().equals(record.get("packagedResource"))) {
                throw new IllegalStateException(
                    "Embedded runtime path mismatch for " + expected.platformId() + ": " + record
                );
            }
            MemberDigest digest = digest(inventory.openStream(expected.path()));
            String manifestHash = record.get("sha256");
            long manifestSize = parseLong(record, "sizeBytes");
            if (!digest.sha256().equals(manifestHash) || digest.size() != manifestSize) {
                throw new IllegalStateException(
                    "Embedded runtime bytes disagree with runtime-distribution manifest for "
                        + expected.platformId()
                );
            }
            if (requirePinnedProductionBytes
                && (digest.size() != expected.size() || !digest.sha256().equals(expected.sha256()))) {
                throw new IllegalStateException(
                    "Embedded runtime identity mismatch for " + expected.platformId()
                        + ": expected size/hash=" + expected.size() + "/" + expected.sha256()
                        + ", actual=" + digest.size() + "/" + digest.sha256()
                );
            }
            report.put("embeddedRuntime." + expected.platformId() + ".path", expected.path());
            report.put("embeddedRuntime." + expected.platformId() + ".size", digest.size());
            report.put("embeddedRuntime." + expected.platformId() + ".sha256", digest.sha256());
        }

        List<Map<String, String>> extensionRecords = parseObjectArray(manifest, "extensionPlatforms");
        if (extensionRecords.size() != OPTIONAL_EXTENSIONS.size()) {
            throw new IllegalStateException(
                "Optional runtime extension count mismatch: expected=" + OPTIONAL_EXTENSIONS.size()
                    + ", actual=" + extensionRecords.size()
            );
        }
        Map<String, Map<String, String>> extensionsById = new LinkedHashMap<>();
        for (Map<String, String> record : extensionRecords) {
            extensionsById.put(record.get("id"), record);
        }
        for (OptionalRuntimeExtension extension : OPTIONAL_EXTENSIONS) {
            Map<String, String> record = extensionsById.get(extension.platformId());
            if (record == null
                || !extension.filename().equals(record.get("extensionFilename"))
                || !extension.sha256().equals(record.get("sha256"))
                || extension.size() != parseLong(record, "sizeBytes")) {
                throw new IllegalStateException(
                    "Optional runtime extension metadata mismatch for " + extension.platformId()
                );
            }
        }
        report.put(
            "optionalRuntimeExtensionPlatforms",
            OPTIONAL_EXTENSIONS.stream().map(OptionalRuntimeExtension::platformId).toList()
        );
        report.put("optionalRuntimeExtensionsIncluded", false);
        return report;
    }

    private static void rejectForbiddenPayloads(FoundationSupport.ArchiveInventory inventory) {
        List<String> forbidden = new ArrayList<>();
        for (String name : inventory.names()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".java")
                || lower.contains("java21-runtimes.zip")
                || lower.contains("lwjgl3ify-wdg-java21-runtimes")
                || lower.startsWith("runtimes/")
                || lower.contains("bundled-client")) {
                forbidden.add(name);
            }
        }
        if (!forbidden.isEmpty()) {
            throw new IllegalStateException(
                "Production lwjgl3ify JAR contains obsolete split-runtime, overlay, or source payloads: "
                    + forbidden
            );
        }
    }

    private static List<Map<String, String>> parseObjectArray(String json, String field) {
        Pattern arrayPattern = Pattern.compile(
            "\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\[(.*?)]",
            Pattern.DOTALL
        );
        Matcher array = arrayPattern.matcher(json);
        if (!array.find()) {
            throw new IllegalStateException("Runtime-distribution JSON array is missing: " + field);
        }
        List<Map<String, String>> records = new ArrayList<>();
        Matcher objectMatcher = Pattern.compile("\\{([^{}]*)}", Pattern.DOTALL).matcher(array.group(1));
        while (objectMatcher.find()) {
            String object = objectMatcher.group(1);
            Map<String, String> values = new LinkedHashMap<>();
            Matcher stringField = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                .matcher(object);
            while (stringField.find()) {
                values.put(stringField.group(1), stringField.group(2));
            }
            Matcher numberField = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*([0-9]+)")
                .matcher(object);
            while (numberField.find()) {
                values.put(numberField.group(1), numberField.group(2));
            }
            records.add(Map.copyOf(values));
        }
        return List.copyOf(records);
    }

    private static void requireJsonString(String json, String field, String expected) {
        String actual = FoundationSupport.extractJsonString(json, field);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                "Runtime-distribution field mismatch for " + field + ": expected=" + expected
                    + ", actual=" + actual
            );
        }
    }

    private static long parseLong(Map<String, String> record, String field) {
        String value = record.get(field);
        if (value == null || !value.matches("[0-9]+")) {
            throw new IllegalStateException("Runtime-distribution field is missing or invalid: " + field);
        }
        return Long.parseLong(value);
    }

    private static MemberDigest digest(InputStream input) throws IOException {
        try (InputStream source = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            long size = 0L;
            int read;
            while ((read = source.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
                size += read;
            }
            return new MemberDigest(size, hex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(String.format(Locale.ROOT, "%02x", current & 0xff));
        }
        return value.toString();
    }

    private record MemberDigest(long size, String sha256) {}

    public static void validateCandidateName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar")) {
            throw new IllegalStateException("lwjgl3ify production input is not a JAR: " + name);
        }
        for (String rejected : List.of(
            "-dev.jar",
            "-dev-preshadow.jar",
            "-sources.jar",
            "-api.jar",
            "-forgepatches.jar"
        )) {
            if (lower.endsWith(rejected)) {
                throw new IllegalStateException(
                    "Development, classified, or sources lwjgl3ify artifact rejected as production: " + name
                );
            }
        }
    }
}
