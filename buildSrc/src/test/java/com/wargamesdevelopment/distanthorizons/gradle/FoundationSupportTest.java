package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class FoundationSupportTest {

    @Test
    public void unifiedVersionParsingAcceptsCurrentVersion() {
        assertEquals("3.0.4-b-wdg-rc.1", FoundationSupport.validateVersion(" 3.0.4-b-wdg-rc.1 "));
    }

    @Test
    public void unifiedVersionParsingAcceptsWdgPrereleaseAndBuildMetadata() {
        assertEquals(
            "3.0.28-master.5+d7e60f5a0d",
            FoundationSupport.validateVersion("3.0.28-master.5+d7e60f5a0d")
        );
    }

    @Test
    public void unifiedVersionParsingRejectsPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> FoundationSupport.validateVersion("GRADLETOKEN_VERSION"));
    }

    @Test
    public void versionMismatchIsRejected() {
        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.requireVersionMatch("3.0.4-b-wdg-rc.1", "3.0.3", "fixture")
        );
        assertTrue(failure.getMessage().contains("version mismatch"));
    }

    @Test
    public void placeholderMetadataIsRejected() {
        assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.rejectTemplateMarkers("author=SinTho0r4s", "fixture")
        );
    }

    @Test
    public void localOverridePathPreservesSpaces() throws Exception {
        Path directory = Files.createTempDirectory("dh local override with spaces ");
        Path artifact = Files.write(directory.resolve("lwjgl3ify dev artifact.jar"), new byte[] {1});
        assertEquals(artifact.toAbsolutePath().normalize(), FoundationSupport.resolveLocalPath(artifact.toString()));
        assertEquals(artifact.toFile(), FoundationSupport.requireReadableRegularFile(artifact.toFile(), "local development artifact"));
    }

    @Test
    public void localLwjglDevelopmentArtifactClassificationIsExplicit() {
        FoundationSupport.validateLwjglDevelopmentCandidateName("lwjgl3ify-3.0.28-dev.jar");
        assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.validateLwjglDevelopmentCandidateName("lwjgl3ify-3.0.28.jar")
        );
        assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.validateLwjglDevelopmentCandidateName("lwjgl3ify-3.0.28-dev-preshadow.jar")
        );
    }

    @Test
    public void missingLocalDevelopmentArtifactIsRejected() {
        File missing = new File("missing development lwjgl3ify artifact.jar");
        assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.requireReadableRegularFile(missing, "local development artifact")
        );
    }

    @Test
    public void missingLocalProductionArtifactIsRejected() {
        File missing = new File("missing production lwjgl3ify artifact.jar");
        assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.requireReadableRegularFile(missing, "local production artifact")
        );
    }

    @Test
    public void duplicateArchiveMemberIsRejected() {
        assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.validateArchiveNames(List.of("a.txt", "a.txt"))
        );
    }

    @Test
    public void caseFoldingCollisionIsRejected() {
        assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.validateArchiveNames(List.of("Mixins.json", "mixins.json"))
        );
    }

    @Test
    public void archiveMemberComparisonStreamsExactBytes() throws Exception {
        Path directory = Files.createTempDirectory("archive stream comparison");
        byte[] bytes = new byte[2 * 1024 * 1024];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 31);
        }
        Path expected = Files.write(directory.resolve("expected.bin"), bytes);
        Path archive = directory.resolve("fixture.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("fixture/expected.bin"));
            zip.write(bytes);
            zip.closeEntry();
        }

        try (FoundationSupport.ArchiveInventory inventory =
                 FoundationSupport.ArchiveInventory.open(archive.toFile())) {
            assertTrue(inventory.contentEquals("fixture/expected.bin", expected));
            Files.write(expected, new byte[] {1, 2, 3});
            assertTrue(!inventory.contentEquals("fixture/expected.bin", expected));
        }
    }

    @Test
    public void wrongClassFileVersionIsObservable() {
        assertEquals(64, FoundationSupport.classMajor(FixtureJars.classBytes(64), "fixture"));
    }
}
