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
        Map<String, Object> reproducibility = CombinedClientSupport.verifyReproducibilityForTesting(
            definition,
            inputs,
            directory.resolve("first.zip"),
            directory.resolve("second.zip")
        );
        assertEquals(true, reproducibility.get("reproducible"));
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

    private static CombinedClientSupport.Inputs fixtureInputs(Path directory, boolean duplicateGtnhLibId)
        throws Exception {
        Path lwjgl = FixtureJars.createLwjgl3ifyJar(directory.resolve("lwjgl3ify-3.0.28.jar"), Set.of());
        Path gtnh = createGtnhLib(directory.resolve("gtnhlib-0.11.31.jar"));
        Path uni = createUniMixins(
            directory.resolve("+unimixins-all-1.7.10-0.1.23.jar"),
            duplicateGtnhLibId
        );
        Path runtime = createRuntimeBundle(directory.resolve("lwjgl3ify-wdg-java21-runtimes.zip"));
        return new CombinedClientSupport.Inputs(
            null,
            null,
            FixtureJars.VERSION,
            lwjgl.toFile(),
            gtnh.toFile(),
            uni.toFile(),
            null,
            runtime.toFile()
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

    private static Path createRuntimeBundle(Path output) throws Exception {
        String root = "lwjgl3ify-wdg-java21-runtimes";
        StringBuilder json = new StringBuilder("{\"javaRuntimeVersion\": \"21.0.11+10-LTS\",\"platforms\":[");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (int index = 0; index < RuntimeArtifactVerifier.RUNTIME_PLATFORMS.size(); index++) {
                String platform = RuntimeArtifactVerifier.RUNTIME_PLATFORMS.get(index);
                if (index > 0) json.append(',');
                json.append("{\"id\": \"").append(platform).append("\"}");
            }
            json.append("]}");
            put(zip, root + "/manifest.json", json.toString().getBytes(StandardCharsets.UTF_8));
            for (String platform : RuntimeArtifactVerifier.RUNTIME_PLATFORMS) {
                String suffix = platform.startsWith("windows-") ? ".zip" : ".tar.gz";
                put(zip, root + "/runtimes/" + platform + suffix, new byte[] {1});
            }
        }
        return output;
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
