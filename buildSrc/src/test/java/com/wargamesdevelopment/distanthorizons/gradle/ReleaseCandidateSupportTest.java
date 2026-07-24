package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class ReleaseCandidateSupportTest {

    @Test
    public void freshProfileUsesExactConservativeNamesAndDoesNotSelectShaders() {
        String config = ReleaseCandidateSupport.freshProfileConfig();
        assertTrue(config.contains("_version = 4"));
        assertTrue(config.contains("qualityPresetSetting = \"LOW\""));
        assertTrue(config.contains("threadPresetSetting = \"MINIMAL_IMPACT\""));
        assertTrue(config.contains("lodChunkRenderDistanceRadius = 128"));
        assertTrue(config.contains("distantGeneratorMode = \"SURFACE\""));
        assertTrue(config.contains("enableAutoUpdater = false"));
        assertTrue(config.contains("enableSilentUpdates = false"));
        assertTrue(!config.toLowerCase().contains("shaderpack"));
    }

    @Test
    public void releaseCandidateUsesEmbeddedRuntimeOneJarAndIsReproducible() throws Exception {
        Path directory = Files.createTempDirectory("release one jar fixture");
        ReleaseCandidateSupport.Inputs inputs = fixtureInputs(directory);
        Path release = directory.resolve("release.zip");
        Map<String, Object> report = ReleaseCandidateSupport.createReleaseArchiveForTesting(
            inputs,
            release
        );
        assertEquals(17, report.get("memberCount"));
        assertEquals(false, report.get("externalRuntimeBundlePresent"));

        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(release.toFile())) {
            assertTrue(inventory.names().stream().noneMatch(name -> name.contains("/lwjgl3ify/runtime/")));
            assertTrue(inventory.names().stream().noneMatch(name -> name.contains("java21-runtimes.zip")));
            String manifest = inventory.text(ReleaseCandidateSupport.ROOT + "/release-manifest.json");
            assertTrue(manifest.contains("\"runtimeDistributionMode\": \"EMBEDDED_PRIMARY_RUNTIMES\""));
            for (Lwjgl3ifyCompatibilityVerifier.RuntimeArchive runtime
                : Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES) {
                assertTrue(manifest.contains("\"path\": \"" + runtime.path() + "\""));
                assertTrue(manifest.contains("\"sha256\": \""
                    + FoundationSupport.sha256(
                        readJarMember(inputs.lwjgl3ify().toPath(), runtime.path())
                    ) + "\""));
            }
        }

        Map<String, Object> reproducibility = ReleaseCandidateSupport.verifyReproducibilityForTesting(
            inputs,
            directory.resolve("first.zip"),
            directory.resolve("second.zip")
        );
        assertEquals(true, reproducibility.get("reproducible"));
    }

    @Test
    public void curseForgeProfileContainsOnlyModsAndNoExternalRuntimeDirectory() throws Exception {
        Path directory = Files.createTempDirectory("curseforge one jar fixture");
        ReleaseCandidateSupport.Inputs inputs = fixtureInputs(directory);
        Path profile = directory.resolve("profile.zip");
        Map<String, Object> report = ReleaseCandidateSupport.createCurseForgeTestingProfileForTesting(
            inputs,
            profile
        );
        assertEquals(9, report.get("memberCount"));
        assertEquals(false, report.get("externalRuntimeBundlePresent"));
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(profile.toFile())) {
            assertTrue(inventory.names().stream().noneMatch(name -> name.contains("overrides/lwjgl3ify/runtime/")));
            assertTrue(inventory.names().stream().noneMatch(name -> name.contains("java21-runtimes.zip")));
        }
    }

    @Test
    public void obsoleteExternalRuntimeMemberIsRejectedFromReleaseArchive() throws Exception {
        Path directory = Files.createTempDirectory("obsolete release runtime fixture");
        ReleaseCandidateSupport.Inputs inputs = fixtureInputs(directory);
        Path valid = directory.resolve("valid.zip");
        ReleaseCandidateSupport.createReleaseArchiveForTesting(inputs, valid);
        Path invalid = directory.resolve("invalid.zip");
        addMember(
            valid,
            invalid,
            ReleaseCandidateSupport.ROOT
                + "/lwjgl3ify/runtime/lwjgl3ify-wdg-java21-runtimes.zip",
            new byte[] {1}
        );
        assertThrows(
            IllegalStateException.class,
            () -> ReleaseCandidateSupport.verifyReleaseArchiveForTesting(inputs, invalid.toFile())
        );
    }

    private static ReleaseCandidateSupport.Inputs fixtureInputs(Path directory) throws Exception {
        Path dh = FixtureJars.createDistantHorizonsJar(
            directory.resolve("distanthorizons-3.0.4-b-wdg-rc.1.jar"),
            Set.of(),
            65
        );
        Path refmap = directory.resolve(FoundationSupport.REFMAP);
        Files.write(refmap, readJarMember(dh, FoundationSupport.REFMAP));
        Path docs = directory.resolve("docs");
        Files.createDirectories(docs);
        for (String name : new String[] {
            "INSTALLATION.md", "UPGRADE.md", "KNOWN_CONFLICTS.md", "PERFORMANCE.md",
            "ROLLBACK.md", "WINDOWS_TESTING.md", "SERVER_COMPATIBILITY.md"
        }) {
            Files.writeString(docs.resolve(name), "fixture " + name + "\n", StandardCharsets.UTF_8);
        }
        return new ReleaseCandidateSupport.Inputs(
            dh.toFile(),
            refmap.toFile(),
            FixtureJars.createLwjgl3ifyJar(
                directory.resolve(Lwjgl3ifyCompatibilityVerifier.EXPECTED_FILENAME),
                Set.of()
            ).toFile(),
            FixtureJars.createGtnhLibJar(directory.resolve(RuntimeArtifactVerifier.GTNHLIB_FILENAME)).toFile(),
            FixtureJars.createUniMixinsJar(directory.resolve(RuntimeArtifactVerifier.UNIMIXINS_FILENAME)).toFile(),
            FixtureJars.createAngelicaJar(directory.resolve(RuntimeArtifactVerifier.ANGELICA_FILENAME)).toFile(),
            "VALIDATION",
            FixtureJars.COMMIT,
            FixtureJars.DIGEST,
            docs.toFile()
        );
    }

    private static byte[] readJarMember(Path jar, String member) throws Exception {
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(jar.toFile())) {
            return inventory.bytes(member);
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
                zip.putNextEntry(new ZipEntry(original.getName()));
                try (var stream = input.getInputStream(original)) {
                    stream.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
    }
}
