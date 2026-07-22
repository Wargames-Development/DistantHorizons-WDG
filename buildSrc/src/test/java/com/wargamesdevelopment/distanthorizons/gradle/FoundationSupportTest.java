package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class FoundationSupportTest {

    @Test
    public void unifiedVersionParsingAcceptsCurrentVersion() {
        assertEquals("3.0.4-b-dev", FoundationSupport.validateVersion(" 3.0.4-b-dev "));
    }

    @Test
    public void unifiedVersionParsingRejectsPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> FoundationSupport.validateVersion("GRADLETOKEN_VERSION"));
    }

    @Test
    public void versionMismatchIsRejected() {
        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> FoundationSupport.requireVersionMatch("3.0.4-b-dev", "3.0.3", "fixture")
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
    public void wrongClassFileVersionIsObservable() {
        assertEquals(64, FoundationSupport.classMajor(FixtureJars.classBytes(64), "fixture"));
    }
}
