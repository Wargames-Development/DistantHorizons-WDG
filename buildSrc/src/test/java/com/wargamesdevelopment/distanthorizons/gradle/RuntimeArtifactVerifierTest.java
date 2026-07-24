package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class RuntimeArtifactVerifierTest {

    @Test
    public void gtnhLibFixtureInPathWithSpacesIsAccepted() throws Exception {
        Path directory = Files.createTempDirectory("GTNHLib artifact with spaces ");
        Path jar = createGtnhLibJar(directory.resolve("gtnhlib-0.11.31.jar"), "0.11.31", "0.11.31");
        Map<String, Object> report = RuntimeArtifactVerifier.verifyGtnhLibFixture(jar.toFile());
        assertEquals("0.11.31", report.get("version"));
        assertEquals(52, report.get("baseClassMajor"));
        assertTrue(String.valueOf(report.get("multiReleaseMajors")).contains("61"));
    }

    @Test
    public void missingArtifactIsRejected() {
        assertThrows(
            IllegalStateException.class,
            () -> RuntimeArtifactVerifier.verifyGtnhLib(Path.of("missing gtnhlib artifact.jar").toFile())
        );
    }

    @Test
    public void wrongGtnhLibVersionIsRejected() throws Exception {
        Path jar = createGtnhLibJar(
            Files.createTempDirectory("wrong gtnh version").resolve("gtnhlib-0.11.31.jar"),
            "0.9.46",
            "0.9.46"
        );
        assertThrows(IllegalStateException.class, () -> RuntimeArtifactVerifier.verifyGtnhLibFixture(jar.toFile()));
    }

    @Test
    public void noGitTagGtnhLibVersionIsRejected() throws Exception {
        Path jar = createGtnhLibJar(
            Files.createTempDirectory("no git tag gtnh").resolve("gtnhlib-0.11.31.jar"),
            "0.11.31",
            "NO-GIT-TAG-SET"
        );
        assertThrows(IllegalStateException.class, () -> RuntimeArtifactVerifier.verifyGtnhLibFixture(jar.toFile()));
    }

    @Test
    public void developmentGtnhLibArtifactIsRejected() throws Exception {
        Path jar = createGtnhLibJar(
            Files.createTempDirectory("development gtnh").resolve("gtnhlib-0.11.31-dev.jar"),
            "0.11.31",
            "0.11.31"
        );
        assertThrows(IllegalStateException.class, () -> RuntimeArtifactVerifier.verifyGtnhLibFixture(jar.toFile()));
    }

    @Test
    public void missingExpectedGtnhLibNestedJarIsRejected() throws Exception {
        Path jar = createGtnhLibJar(
            Files.createTempDirectory("missing gtnh nested jar").resolve("gtnhlib-0.11.31.jar"),
            "0.11.31",
            "0.11.31",
            Map.of()
        );

        assertThrows(
            IllegalStateException.class,
            () -> RuntimeArtifactVerifier.verifyGtnhLibFixture(jar.toFile())
        );
    }

    @Test
    public void additionalGtnhLibNestedJarIsRejected() throws Exception {
        Path jar = createGtnhLibJar(
            Files.createTempDirectory("additional gtnh nested jar").resolve("gtnhlib-0.11.31.jar"),
            "0.11.31",
            "0.11.31",
            Map.of(
                RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR,
                "fixture nested jar".getBytes(StandardCharsets.UTF_8),
                "unexpected-loader.jar",
                "unexpected nested jar".getBytes(StandardCharsets.UTF_8)
            )
        );

        assertThrows(
            IllegalStateException.class,
            () -> RuntimeArtifactVerifier.verifyGtnhLibFixture(jar.toFile())
        );
    }

    @Test
    public void wrongProductionGtnhLibOuterBytesAreRejectedBeforeArchiveTrust() throws Exception {
        Path jar = Files.write(
            Files.createTempDirectory("wrong gtnh outer bytes")
                .resolve(RuntimeArtifactVerifier.GTNHLIB_FILENAME),
            new byte[] {1, 2, 3}
        );

        assertThrows(
            IllegalStateException.class,
            () -> RuntimeArtifactVerifier.verifyGtnhLib(jar.toFile())
        );
    }

    @Test
    public void wrongProductionGtnhLibNestedJarBytesAreRejected() throws Exception {
        Path jar = createGtnhLibJar(
            Files.createTempDirectory("wrong gtnh nested bytes")
                .resolve(RuntimeArtifactVerifier.GTNHLIB_FILENAME),
            RuntimeArtifactVerifier.GTNHLIB_VERSION,
            RuntimeArtifactVerifier.GTNHLIB_VERSION,
            Map.of(
                RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR,
                "wrong production nested bytes".getBytes(StandardCharsets.UTF_8)
            )
        );

        try (FoundationSupport.ArchiveInventory inventory =
            FoundationSupport.ArchiveInventory.open(jar.toFile())) {
            assertThrows(
                IllegalStateException.class,
                () -> RuntimeArtifactVerifier.verifyExpectedNestedJar(
                    inventory,
                    RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR,
                    RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR_SIZE,
                    RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR_SHA256,
                    true
                )
            );
        }
    }

    @Test
    public void alteredGtnhLibNestedJarPathIsRejected() throws Exception {
        Path jar = createGtnhLibJar(
            Files.createTempDirectory("wrong gtnh nested path").resolve("gtnhlib-0.11.31.jar"),
            "0.11.31",
            "0.11.31",
            Map.of(
                "nested/fplib_deploader.jar",
                "fixture nested jar".getBytes(StandardCharsets.UTF_8)
            )
        );

        assertThrows(
            IllegalStateException.class,
            () -> RuntimeArtifactVerifier.verifyGtnhLibFixture(jar.toFile())
        );
    }

    @Test
    public void wrongAngelicaBytesAreRejectedBeforeFilenameTrust() throws Exception {
        Path jar = Files.write(
            Files.createTempDirectory("wrong angelica").resolve("angelica-2.1.54.jar"),
            new byte[] {1, 2, 3}
        );
        assertThrows(IllegalStateException.class, () -> RuntimeArtifactVerifier.verifyAngelica(jar.toFile()));
    }

    @Test
    public void wrongUniMixinsBytesAreRejectedBeforeFilenameTrust() throws Exception {
        Path jar = Files.write(
            Files.createTempDirectory("wrong unimixins").resolve("+unimixins-all-1.7.10-0.1.23.jar"),
            new byte[] {1, 2, 3}
        );
        assertThrows(IllegalStateException.class, () -> RuntimeArtifactVerifier.verifyUniMixins(jar.toFile()));
    }

    @Test
    public void missingGtnhMixinsIdentityIsRejected() {
        String mcmod = "[{\"modid\":\"unimixins\",\"version\":\"0.1.23\",\"mcversion\":\"1.7.10\"}]";
        assertThrows(
            IllegalStateException.class,
            () -> RuntimeArtifactVerifier.requireIdentity(mcmod, "gtnhmixins", null, "1.7.10")
        );
    }

    @Test
    public void compositeModIdentitiesAreKeptInsideOneJar() {
        String mcmod = "["
            + "{\"modid\":\"unimixins\",\"version\":\"0.1.23\",\"mcversion\":\"1.7.10\"},"
            + "{\"modid\":\"gtnhmixins\",\"version\":\"2.2.0+uni.0.1.23\",\"mcversion\":\"1.7.10\"}"
            + "]";
        assertEquals(2, RuntimeArtifactVerifier.readModIdentities(mcmod).size());
    }

    @Test
    public void legacySplitRuntimeAuditCanStillInspectSixPlatformBundle() throws Exception {
        Path bundle = createRuntimeBundle(Files.createTempDirectory("runtime bundle").resolve("runtime.zip"), false);
        Map<String, Object> report = RuntimeArtifactVerifier.verifyRuntimeBundle(bundle.toFile());
        assertEquals("21.0.11+10-LTS", report.get("runtimeVersion"));
    }

    @Test
    public void legacySplitRuntimeAuditRejectsMissingPlatform() throws Exception {
        Path bundle = createRuntimeBundle(Files.createTempDirectory("runtime missing platform").resolve("runtime.zip"), true);
        assertThrows(IllegalStateException.class, () -> RuntimeArtifactVerifier.verifyRuntimeBundle(bundle.toFile()));
    }

    @Test
    public void traversalArchivePathIsRejected() throws Exception {
        Path archive = Files.createTempDirectory("traversal archive").resolve("unsafe.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("../evil.txt"));
            output.write(1);
            output.closeEntry();
        }
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(archive.toFile())) {
            assertThrows(IllegalStateException.class, () -> RuntimeArtifactVerifier.validateSafeArchive(inventory));
        }
    }

    @Test
    public void packageMustHaveOneTopLevelDirectory() {
        assertThrows(
            IllegalStateException.class,
            () -> RuntimeArtifactVerifier.singleRoot(java.util.List.of("first/a", "second/b"))
        );
    }

    private static Path createGtnhLibJar(
        Path jar,
        String metadataVersion,
        String tagsVersion
    ) throws IOException {
        return createGtnhLibJar(
            jar,
            metadataVersion,
            tagsVersion,
            Map.of(
                RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR,
                "fixture nested jar".getBytes(StandardCharsets.UTF_8)
            )
        );
    }

    private static Path createGtnhLibJar(
        Path jar,
        String metadataVersion,
        String tagsVersion,
        Map<String, byte[]> nestedEntries
    ) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Multi-Release", "true");

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
            "mcmod.info",
            ("[{\"modid\":\"gtnhlib\",\"version\":\"" + metadataVersion
                + "\",\"mcversion\":\"1.7.10\"}]").getBytes(StandardCharsets.UTF_8)
        );
        entries.put("com/gtnewhorizon/gtnhlib/GTNHLib.class", FixtureJars.classBytes(52, tagsVersion));
        entries.put("com/gtnewhorizon/gtnhlib/core/GTNHLibCore.class", FixtureJars.classBytes(52));
        entries.put(
            "com/gtnewhorizon/gtnhlib/core/GTNHLibCoreModContainer.class",
            FixtureJars.classBytes(52, tagsVersion)
        );
        entries.put("META-INF/versions/17/com/gtnewhorizon/gtnhlib/Java17.class", FixtureJars.classBytes(61));
        entries.put("META-INF/gtnhlib_at.cfg", "public fixture".getBytes(StandardCharsets.UTF_8));
        entries.put("mixins.gtnhlib.json", "{\"required\":true}".getBytes(StandardCharsets.UTF_8));
        entries.put("mixins.gtnhlib.early.json", "{\"required\":true}".getBytes(StandardCharsets.UTF_8));
        entries.put(
            "META-INF/rfb-plugin/gtnhlib.properties",
            ("version=" + metadataVersion).getBytes(StandardCharsets.UTF_8)
        );
        entries.putAll(nestedEntries);
        writeJar(jar, manifest, entries);
        return jar;
    }

    private static Path createRuntimeBundle(Path output, boolean omitLastPlatform) throws IOException {
        String root = "lwjgl3ify-wdg-java21-runtimes";
        StringBuilder manifest = new StringBuilder("{\"javaRuntimeVersion\": \"21.0.11+10-LTS\", \"platforms\": [");
        int limit = omitLastPlatform ? RuntimeArtifactVerifier.RUNTIME_PLATFORMS.size() - 1
            : RuntimeArtifactVerifier.RUNTIME_PLATFORMS.size();
        for (int index = 0; index < limit; index++) {
            if (index > 0) manifest.append(',');
            manifest.append("{\"id\": \"").append(RuntimeArtifactVerifier.RUNTIME_PLATFORMS.get(index)).append("\"}");
        }
        manifest.append("]}");
        Files.createDirectories(output.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            put(zip, root + "/manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
            for (int index = 0; index < limit; index++) {
                String platform = RuntimeArtifactVerifier.RUNTIME_PLATFORMS.get(index);
                String suffix = platform.startsWith("windows-") ? ".zip" : ".tar.gz";
                put(zip, root + "/runtimes/" + platform + suffix, new byte[] {(byte) index});
            }
        }
        return output;
    }

    private static void writeJar(Path jar, Manifest manifest, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static void put(ZipOutputStream output, String name, byte[] bytes) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
}
