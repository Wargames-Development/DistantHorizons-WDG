package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ModpackAuditSupportTest {

    @Test
    public void duplicateModIdsAndStandaloneCompositeComponentsAreRejected() {
        var result = ModpackAuditSupport.analyze(List.of(
            new ModpackAuditSupport.JarRecord("mods/a.jar", "a.jar", "a".repeat(64), Set.of("distanthorizons")),
            new ModpackAuditSupport.JarRecord("mods/b.jar", "b.jar", "b".repeat(64), Set.of("distanthorizons")),
            new ModpackAuditSupport.JarRecord("mods/GTNHMixins.jar", "GTNHMixins.jar", "c".repeat(64), Set.of("gtnhmixins"))
        ));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("Duplicate mod ID")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("Standalone GTNHMixins")));
    }

    @Test
    public void compositeComponentsInsideTheirOwningJarAreNotFilenameConflicts() {
        var result = ModpackAuditSupport.analyze(List.of(
            new ModpackAuditSupport.JarRecord("mods/+unimixins-all.jar", "+unimixins-all.jar", "a".repeat(64), Set.of("unimixins", "gtnhmixins")),
            new ModpackAuditSupport.JarRecord("mods/angelica.jar", "angelica.jar", "b".repeat(64), Set.of("angelica", "mcpatcherforge", "notfine")),
            new ModpackAuditSupport.JarRecord("mods/dh.jar", "dh.jar", "c".repeat(64), Set.of("distanthorizons")),
            new ModpackAuditSupport.JarRecord("mods/lwjgl3ify.jar", "lwjgl3ify.jar", "d".repeat(64), Set.of("lwjgl3ify")),
            new ModpackAuditSupport.JarRecord("mods/gtnhlib.jar", "gtnhlib.jar", "e".repeat(64), Set.of("gtnhlib"))
        ));
        assertTrue(result.errors().stream().noneMatch(value -> value.contains("MCPatcherForge") || value.contains("NotFine") || value.contains("GTNHMixins")));
    }

    @Test
    public void exactRcStackNamesAreAcceptedButOldOrAlternateVersionsAreRejected() {
        var accepted = ModpackAuditSupport.analyze(List.of(
            new ModpackAuditSupport.JarRecord("mods/distanthorizons-3.0.4-b-wdg-rc.1.jar", "distanthorizons-3.0.4-b-wdg-rc.1.jar", "a", Set.of("distanthorizons")),
            new ModpackAuditSupport.JarRecord("mods/lwjgl3ify-3.0.28-master.5+d7e60f5a0d.jar", "lwjgl3ify-3.0.28-master.5+d7e60f5a0d.jar", "b", Set.of("lwjgl3ify")),
            new ModpackAuditSupport.JarRecord("mods/gtnhlib-0.11.31.jar", "gtnhlib-0.11.31.jar", "c", Set.of("gtnhlib")),
            new ModpackAuditSupport.JarRecord("mods/+unimixins-all-1.7.10-0.1.23.jar", "+unimixins-all-1.7.10-0.1.23.jar", "d", Set.of("unimixins")),
            new ModpackAuditSupport.JarRecord("mods/angelica-2.1.54.jar", "angelica-2.1.54.jar", "e", Set.of("angelica"))
        ));
        assertTrue(accepted.errors().isEmpty());

        var rejected = ModpackAuditSupport.analyze(List.of(
            new ModpackAuditSupport.JarRecord("mods/DistantHorizons-3.0.4-b-dev.jar", "DistantHorizons-3.0.4-b-dev.jar", "f", Set.of("distanthorizons")),
            new ModpackAuditSupport.JarRecord("mods/lwjgl3ify-3.0.27.jar", "lwjgl3ify-3.0.27.jar", "g", Set.of("lwjgl3ify")),
            new ModpackAuditSupport.JarRecord("mods/angelica-2.1.53.jar", "angelica-2.1.53.jar", "h", Set.of("angelica"))
        ));
        assertTrue(rejected.errors().stream().anyMatch(value -> value.contains("old Distant Horizons")));
        assertTrue(rejected.errors().stream().anyMatch(value -> value.contains("old lwjgl3ify")));
        assertTrue(rejected.errors().stream().anyMatch(value -> value.contains("Another Angelica")));
    }

    @Test
    public void dedicatedServerAuditDetectsClientJarsAndRuntimeBundleButLeavesSharedLibrariesAlone() {
        List<String> conflicts = ModpackAuditSupport.clientOnlyServerArtifacts(List.of(
            "mods/distanthorizons-3.0.4-b-wdg-rc.1.jar",
            "mods/gtnhlib-0.11.31.jar",
            "mods/+unimixins-all-1.7.10-0.1.23.jar",
            "lwjgl3ify/runtime/lwjgl3ify-wdg-java21-runtimes.zip",
            "mods/angelica-2.1.54.jar"
        ));
        assertEquals(3, conflicts.size());
        assertTrue(conflicts.stream().anyMatch(value -> value.contains("distanthorizons")));
        assertTrue(conflicts.stream().anyMatch(value -> value.contains("java21-runtimes")));
        assertTrue(conflicts.stream().anyMatch(value -> value.contains("angelica")));
    }
}
