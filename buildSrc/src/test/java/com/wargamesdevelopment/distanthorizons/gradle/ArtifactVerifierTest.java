package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class ArtifactVerifierTest {

    @Test
    public void productionLwjgl3ifyFixtureIsAccepted() throws Exception {
        Path dir = Files.createTempDirectory("lwjgl fixture");
        Path jar = FixtureJars.createLwjgl3ifyJar(dir.resolve("lwjgl3ify-3.0.28.jar"), Set.of());
        Map<String, Object> report = Lwjgl3ifyCompatibilityVerifier.verify(jar.toFile());
        assertEquals(true, report.get("compatible"));
    }

    @Test
    public void developmentLwjgl3ifyArtifactIsRejectedAsProduction() {
        assertThrows(
            IllegalStateException.class,
            () -> Lwjgl3ifyCompatibilityVerifier.validateCandidateName("lwjgl3ify-3.0.28-dev.jar")
        );
    }

    @Test
    public void incompleteAutomaticRuntimeLwjgl3ifyIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("lwjgl missing coordinator");
        Path jar = FixtureJars.createLwjgl3ifyJar(
            dir.resolve("lwjgl3ify-3.0.28.jar"),
            Set.of("me/eigenraven/lwjgl3ify/relauncher/runtime/AutomaticRuntimeCoordinator.class")
        );
        assertThrows(IllegalStateException.class, () -> Lwjgl3ifyCompatibilityVerifier.verify(jar.toFile()));
    }

    @Test
    public void productionDistantHorizonsFixtureIsAccepted() throws Exception {
        Path dir = Files.createTempDirectory("dh fixture");
        Path jar = FixtureJars.createDistantHorizonsJar(dir.resolve("distanthorizons-3.0.4-b-dev.jar"), Set.of(), 65);
        Path refmap = Files.write(dir.resolve(FoundationSupport.REFMAP), readJarMember(jar, FoundationSupport.REFMAP));
        Map<String, Object> report = DistantHorizonsArtifactVerifier.verify(jar.toFile(), refmap.toFile(), FixtureJars.VERSION);
        assertEquals("reobfuscated-shadow", report.get("productionIdentity"));
    }

    @Test
    public void generatedVersionTokenClassMayBeCompileOnly() throws Exception {
        Path dir = Files.createTempDirectory("dh token compile only");
        Path jar = FixtureJars.createDistantHorizonsJar(
            dir.resolve("distanthorizons-3.0.4-b-dev.jar"),
            Set.of("com/seibel/distanthorizons/coreapi/Tags.class"),
            65
        );
        Path refmap = Files.write(dir.resolve(FoundationSupport.REFMAP), readJarMember(jar, FoundationSupport.REFMAP));
        Map<String, Object> report = DistantHorizonsArtifactVerifier.verify(
            jar.toFile(),
            refmap.toFile(),
            FixtureJars.VERSION
        );
        assertEquals(false, report.get("generatedVersionTokenClassPackaged"));
        assertEquals(FixtureJars.VERSION, report.get("modInfoVersionConstant"));
    }

    @Test
    public void emptyProductionRefmapIsRejected() throws Exception {
        assertDhRefmapFailure("{\"mappings\":{},\"data\":{\"searge\":{}}}");
    }

    @Test
    public void dotSeparatedRefmapOwnersAreRejected() throws Exception {
        assertDhRefmapFailure(
            "{\"mappings\":{\"com.seibel.distanthorizons.mixin.MixinMinecraft\":{\"x\":\"field_1:Lnet/minecraft/X;\"}},"
                + "\"data\":{\"searge\":{\"com.seibel.distanthorizons.mixin.MixinMinecraft\":{\"x\":\"field_1:Lnet/minecraft/X;\"}}}}"
        );
    }

    @Test
    public void missingMetadataIsRejected() throws Exception {
        assertDhFailure(Set.of("mcmod.info"), 65);
    }

    @Test
    public void missingMixinRefmapIsRejected() throws Exception {
        assertDhFailure(Set.of(FoundationSupport.REFMAP), 65);
    }

    @Test
    public void missingAccessTransformerIsRejected() throws Exception {
        assertDhFailure(Set.of(FoundationSupport.ACCESS_TRANSFORMER), 65);
    }

    @Test
    public void missingSqlMigrationListIsRejected() throws Exception {
        assertDhFailure(Set.of("sqlScripts/scriptList.txt"), 65);
    }

    @Test
    public void wrongProductionClassVersionIsRejected() throws Exception {
        assertDhFailure(Set.of(), 64);
    }

    @Test
    public void developmentDistantHorizonsArtifactIsRejectedAsProduction() {
        assertThrows(
            IllegalStateException.class,
            () -> DistantHorizonsArtifactVerifier.validateCandidateName(
                "distanthorizons-3.0.4-b-dev-dev.jar", FixtureJars.VERSION
            )
        );
    }

    @Test
    public void validPublishedDependencyClassificationIsAccepted() throws Exception {
        Path dir = Files.createTempDirectory("metadata fixture");
        Path pom = dir.resolve("pom.xml");
        Path module = dir.resolve("module.json");
        FixtureJars.writeMetadata(pom, module, null);
        Map<String, Object> report = PublishedMetadataVerifier.verify(pom.toFile(), module.toFile(), FixtureJars.VERSION);
        assertEquals(false, report.get("optionalIntegrationsMandatory"));
    }

    @Test
    public void missingRequiredPublishedDependencyIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("metadata missing required");
        Path pom = dir.resolve("pom.xml");
        Path module = dir.resolve("module.json");
        FixtureJars.writeMetadata(pom, module, null);
        Files.writeString(
            pom,
            Files.readString(pom).replace(
                "<dependency><groupId>com.github.GTNewHorizons</groupId><artifactId>UniMixins</artifactId><version>0.1</version></dependency>",
                ""
            )
        );
        Files.writeString(module, Files.readString(module).replace(",\"unimixins\"", ""));
        assertThrows(
            IllegalStateException.class,
            () -> PublishedMetadataVerifier.verify(pom.toFile(), module.toFile(), FixtureJars.VERSION)
        );
    }

    @Test
    public void everyOptionalIntegrationIsRejectedAsMandatory() throws Exception {
        for (String marker : new String[] {"Hodgepodge", "NotEnoughItems", "Angelica", "GT5-Unofficial", "rple-mc1.7.10"}) {
            Path dir = Files.createTempDirectory("metadata " + marker);
            Path pom = dir.resolve("pom.xml");
            Path module = dir.resolve("module.json");
            FixtureJars.writeMetadata(pom, module, marker);
            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> PublishedMetadataVerifier.verify(pom.toFile(), module.toFile(), FixtureJars.VERSION)
            );
            assertTrue(failure.getMessage().contains("published as mandatory"));
        }
    }

    @Test
    public void metadataVersionMismatchIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("metadata version mismatch");
        Path pom = dir.resolve("pom.xml");
        Path module = dir.resolve("module.json");
        FixtureJars.writeMetadata(pom, module, null);
        assertThrows(
            IllegalStateException.class,
            () -> PublishedMetadataVerifier.verify(pom.toFile(), module.toFile(), "3.0.5")
        );
    }

    private static void assertDhRefmapFailure(String refmapText) throws Exception {
        Path dir = Files.createTempDirectory("dh invalid refmap fixture");
        Path jar = FixtureJars.createDistantHorizonsJar(
            dir.resolve("distanthorizons-3.0.4-b-dev.jar"),
            Set.of(),
            65,
            refmapText
        );
        Path refmap = Files.writeString(dir.resolve(FoundationSupport.REFMAP), refmapText);
        assertThrows(
            IllegalStateException.class,
            () -> DistantHorizonsArtifactVerifier.verify(jar.toFile(), refmap.toFile(), FixtureJars.VERSION)
        );
    }

    private static void assertDhFailure(Set<String> omitted, int classMajor) throws Exception {
        Path dir = Files.createTempDirectory("dh failing fixture");
        Path jar = FixtureJars.createDistantHorizonsJar(dir.resolve("distanthorizons-3.0.4-b-dev.jar"), omitted, classMajor);
        Path refmap = dir.resolve(FoundationSupport.REFMAP);
        if (omitted.contains(FoundationSupport.REFMAP)) {
            Files.writeString(refmap, FixtureJars.validDistantHorizonsRefmap());
        } else {
            Files.write(refmap, readJarMember(jar, FoundationSupport.REFMAP));
        }
        assertThrows(
            IllegalStateException.class,
            () -> DistantHorizonsArtifactVerifier.verify(jar.toFile(), refmap.toFile(), FixtureJars.VERSION)
        );
    }

    private static byte[] readJarMember(Path jar, String member) throws Exception {
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(jar.toFile())) {
            return inventory.bytes(member);
        }
    }
}
