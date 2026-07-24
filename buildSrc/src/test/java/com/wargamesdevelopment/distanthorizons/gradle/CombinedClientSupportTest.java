package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class CombinedClientSupportTest {

    @Test
    public void activeInputContractsRequireNoLegacySplitRuntimeProperties() {
        Set<String> combinedComponents = java.util.Arrays
            .stream(CombinedClientSupport.Inputs.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> releaseComponents = java.util.Arrays
            .stream(ReleaseCandidateSupport.Inputs.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());

        assertEquals(false, combinedComponents.contains("runtimeBundle"));
        assertEquals(false, combinedComponents.contains("bundledClientPackage"));
        assertEquals(false, releaseComponents.contains("runtimeBundle"));
        assertEquals(false, releaseComponents.contains("bundledClientPackage"));
    }

    @Test
    public void correctBootstrapFixturePackageIsAcceptedAndReproducible() throws Exception {
        Path directory = Files.createTempDirectory("combined client fixture with spaces ");
        CombinedClientSupport.Inputs inputs = fixtureInputs(directory, false);
        CombinedClientSupport.PackageDefinition definition = new CombinedClientSupport.PackageDefinition(
            "bootstrap-smoke",
            "DistantHorizons-WDG-Bootstrap-Smoke",
            false,
            false
        );
        Path packageFile = directory.resolve("stage-a.zip");
        Map<String, Object> report = CombinedClientSupport.createPackageForTesting(definition, inputs, packageFile);
        assertEquals(3, report.get("modJarCount"));
        assertEquals(5, report.get("memberCount"));
        assertEquals(false, report.get("externalRuntimeBundlePresent"));
        Map<String, Object> reproducibility = CombinedClientSupport.verifyReproducibilityForTesting(
            definition,
            inputs,
            directory.resolve("first.zip"),
            directory.resolve("second.zip")
        );
        assertEquals(true, reproducibility.get("reproducible"));
    }

    @Test
    public void stageBAndStageCMembershipUseOnlyModJars() throws Exception {
        Path directory = Files.createTempDirectory("stage membership fixtures");
        CombinedClientSupport.Inputs inputs = fullFixtureInputs(directory);

        CombinedClientSupport.PackageDefinition stageB = new CombinedClientSupport.PackageDefinition(
            "distant-horizons-required-smoke",
            "DistantHorizons-WDG-Stage-B",
            true,
            false
        );
        Map<String, Object> stageBReport = CombinedClientSupport.createPackageForTesting(
            stageB,
            inputs,
            directory.resolve("stage-b.zip")
        );
        assertEquals(4, stageBReport.get("modJarCount"));
        assertEquals(6, stageBReport.get("memberCount"));
        assertEquals(false, stageBReport.get("externalRuntimeBundlePresent"));

        CombinedClientSupport.PackageDefinition stageC = new CombinedClientSupport.PackageDefinition(
            "combined-client",
            "DistantHorizons-WDG-Stage-C",
            true,
            true
        );
        Map<String, Object> stageCReport = CombinedClientSupport.createPackageForTesting(
            stageC,
            inputs,
            directory.resolve("stage-c.zip")
        );
        assertEquals(5, stageCReport.get("modJarCount"));
        assertEquals(7, stageCReport.get("memberCount"));
        assertEquals(false, stageCReport.get("externalRuntimeBundlePresent"));
    }

    @Test
    public void duplicateModIdAcrossSeparateJarsIsRejected() throws Exception {
        Path directory = Files.createTempDirectory("duplicate package mod id");
        CombinedClientSupport.Inputs inputs = fixtureInputs(directory, true);
        CombinedClientSupport.PackageDefinition definition = new CombinedClientSupport.PackageDefinition(
            "bootstrap-smoke",
            "DistantHorizons-WDG-Bootstrap-Smoke",
            false,
            false
        );
        assertThrows(
            IllegalStateException.class,
            () -> CombinedClientSupport.createPackageForTesting(definition, inputs, directory.resolve("bad.zip"))
        );
    }

    @Test
    public void manifestHashMismatchIsRejected() throws Exception {
        Path directory = Files.createTempDirectory("manifest hash mismatch");
        CombinedClientSupport.Inputs inputs = fixtureInputs(directory, false);
        CombinedClientSupport.PackageDefinition definition = new CombinedClientSupport.PackageDefinition(
            "bootstrap-smoke",
            "DistantHorizons-WDG-Bootstrap-Smoke",
            false,
            false
        );
        Path valid = directory.resolve("valid.zip");
        CombinedClientSupport.createPackageForTesting(definition, inputs, valid);
        Path corrupted = directory.resolve("corrupted.zip");
        rewriteManifest(valid, corrupted);
        assertThrows(
            IllegalStateException.class,
            () -> CombinedClientSupport.verifyPackageForTesting(definition, inputs, corrupted.toFile())
        );
    }

    @Test
    public void obsoleteExternalRuntimeZipMemberIsRejected() throws Exception {
        Path directory = Files.createTempDirectory("obsolete external runtime package");
        CombinedClientSupport.Inputs inputs = fixtureInputs(directory, false);
        CombinedClientSupport.PackageDefinition definition = new CombinedClientSupport.PackageDefinition(
            "bootstrap-smoke",
            "DistantHorizons-WDG-Bootstrap-Smoke",
            false,
            false
        );
        Path valid = directory.resolve("valid.zip");
        CombinedClientSupport.createPackageForTesting(definition, inputs, valid);
        Path invalid = directory.resolve("invalid.zip");
        addMember(
            valid,
            invalid,
            "DistantHorizons-WDG-Bootstrap-Smoke/lwjgl3ify/runtime/lwjgl3ify-wdg-java21-runtimes.zip",
            new byte[] {1}
        );
        assertThrows(
            IllegalStateException.class,
            () -> CombinedClientSupport.verifyPackageForTesting(definition, inputs, invalid.toFile())
        );
    }

    private static CombinedClientSupport.Inputs fullFixtureInputs(Path directory) throws Exception {
        Path dh = FixtureJars.createDistantHorizonsJar(
            directory.resolve("distanthorizons-3.0.4-b-wdg-rc.1.jar"),
            Set.of(),
            65
        );
        Path refmap = directory.resolve(FoundationSupport.REFMAP);
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(dh.toFile())) {
            Files.write(refmap, inventory.bytes(FoundationSupport.REFMAP));
        }
        return new CombinedClientSupport.Inputs(
            dh.toFile(),
            refmap.toFile(),
            FixtureJars.VERSION,
            FixtureJars.createLwjgl3ifyJar(
                directory.resolve(Lwjgl3ifyCompatibilityVerifier.EXPECTED_FILENAME),
                Set.of()
            ).toFile(),
            FixtureJars.createGtnhLibJar(directory.resolve(RuntimeArtifactVerifier.GTNHLIB_FILENAME)).toFile(),
            FixtureJars.createUniMixinsJar(directory.resolve(RuntimeArtifactVerifier.UNIMIXINS_FILENAME)).toFile(),
            FixtureJars.createAngelicaJar(directory.resolve(RuntimeArtifactVerifier.ANGELICA_FILENAME)).toFile()
        );
    }

    private static CombinedClientSupport.Inputs fixtureInputs(Path directory, boolean duplicateGtnhLibId)
        throws Exception {
        Path lwjgl = FixtureJars.createLwjgl3ifyJar(directory.resolve("lwjgl3ify-3.0.28.jar"), Set.of());
        Path gtnh = createGtnhLib(directory.resolve("gtnhlib-0.11.31.jar"));
        Path uni = createUniMixins(
            directory.resolve("+unimixins-all-1.7.10-0.1.23.jar"),
            duplicateGtnhLibId
        );
        return new CombinedClientSupport.Inputs(
            null,
            null,
            FixtureJars.VERSION,
            lwjgl.toFile(),
            gtnh.toFile(),
            uni.toFile(),
            null
        );
    }

    private static Path createGtnhLib(Path jar) throws Exception {
        Manifest manifest = manifest(Map.of("Multi-Release", "true"));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
            "mcmod.info",
            "[{\"modid\":\"gtnhlib\",\"version\":\"0.11.31\",\"mcversion\":\"1.7.10\"}]"
                .getBytes(StandardCharsets.UTF_8)
        );
        entries.put("com/gtnewhorizon/gtnhlib/GTNHLib.class", FixtureJars.classBytes(52));
        entries.put("com/gtnewhorizon/gtnhlib/Tags.class", FixtureJars.classBytes(52, "0.11.31"));
        entries.put("com/gtnewhorizon/gtnhlib/core/GTNHLibCore.class", FixtureJars.classBytes(52));
        entries.put(
            "com/gtnewhorizon/gtnhlib/core/GTNHLibCoreModContainer.class",
            FixtureJars.classBytes(52, "0.11.31")
        );
        entries.put("META-INF/versions/17/com/gtnewhorizon/gtnhlib/Java17.class", FixtureJars.classBytes(61));
        entries.put("META-INF/gtnhlib_at.cfg", new byte[] {1});
        entries.put("mixins.gtnhlib.json", new byte[] {1});
        entries.put("mixins.gtnhlib.early.json", new byte[] {1});
        entries.put("META-INF/rfb-plugin/gtnhlib.properties", "version=0.11.31".getBytes(StandardCharsets.UTF_8));
        entries.put(
            RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR,
            "fixture nested loader".getBytes(StandardCharsets.UTF_8)
        );
        writeJar(jar, manifest, entries);
        return jar;
    }

    private static Path createUniMixins(Path jar, boolean duplicateGtnhLibId) throws Exception {
        Manifest manifest = manifest(Map.of(
            "FMLCorePlugin", "io.github.legacymoddingmc.unimixins.all.AllCore",
            "TweakClass", "org.spongepowered.asm.launch.MixinTweaker",
            "MixinConfigs", "mixins.gtnhmixins.json"
        ));
        String extra = duplicateGtnhLibId
            ? ",{\"modid\":\"gtnhlib\",\"version\":\"0.11.31\",\"mcversion\":\"1.7.10\"}"
            : "";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
            "mcmod.info",
            ("[{\"modid\":\"unimixins\",\"version\":\"0.1.23\",\"mcversion\":\"1.7.10\"},"
                + "{\"modid\":\"gtnhmixins\",\"version\":\"2.2.0+uni.0.1.23\",\"mcversion\":\"1.7.10\"}"
                + extra + "]").getBytes(StandardCharsets.UTF_8)
        );
        entries.put("io/github/legacymoddingmc/unimixins/all/AllCore.class", FixtureJars.classBytes(52));
        entries.put("com/gtnewhorizon/gtnhmixins/GTNHMixins.class", FixtureJars.classBytes(52));
        entries.put("com/gtnewhorizon/gtnhmixins/core/GTNHMixinsCore.class", FixtureJars.classBytes(52));
        entries.put("mixins.gtnhmixins.json", new byte[] {1});
        entries.put("mixins.gtnhmixins.refmap.json", new byte[] {1});
        writeJar(jar, manifest, entries);
        return jar;
    }

    private static Manifest manifest(Map<String, String> values) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        values.forEach(manifest.getMainAttributes()::putValue);
        return manifest;
    }

    private static void writeJar(Path jar, Manifest manifest, Map<String, byte[]> entries) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static void addMember(Path source, Path output, String name, byte[] content) throws Exception {
        try (
            java.util.zip.ZipFile input = new java.util.zip.ZipFile(source.toFile());
            ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))
        ) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry original = entries.nextElement();
                try (var stream = input.getInputStream(original)) {
                    put(zip, original.getName(), stream.readAllBytes());
                }
            }
            put(zip, name, content);
        }
    }

    private static void rewriteManifest(Path source, Path output) throws Exception {
        try (
            java.util.zip.ZipFile input = new java.util.zip.ZipFile(source.toFile());
            ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))
        ) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry original = entries.nextElement();
                byte[] bytes;
                try (var stream = input.getInputStream(original)) {
                    bytes = stream.readAllBytes();
                }
                if (original.getName().endsWith("/" + CombinedClientSupport.MANIFEST_NAME)) {
                    String text = new String(bytes, StandardCharsets.UTF_8).replaceFirst(
                        "\\\"sha256\\\": \\\"[0-9a-f]{64}\\\"",
                        "\"sha256\": \"0000000000000000000000000000000000000000000000000000000000000000\""
                    );
                    bytes = text.getBytes(StandardCharsets.UTF_8);
                }
                put(zip, original.getName(), bytes);
            }
        }
    }

    private static void put(ZipOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
}
