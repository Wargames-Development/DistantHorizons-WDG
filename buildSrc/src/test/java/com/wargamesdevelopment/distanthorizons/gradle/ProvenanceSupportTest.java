package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class ProvenanceSupportTest {

    @Test
    public void validationRequiresChange006ModifiedTreeAndIsDeterministic() throws Exception {
        Path root = Files.createTempDirectory("provenance fixture");
        Path source = Files.writeString(root.resolve("source.txt"), "fixture\n");
        ProvenanceSupport.Resolved resolved = ProvenanceSupport.resolve(
            root, List.of(source.toFile()), "VALIDATION", ProvenanceSupport.BASE_COMMIT, "master",
            "modified", null, null
        );
        assertEquals(ProvenanceSupport.VALIDATION_SOURCE, resolved.buildSource());
        assertEquals("modified", resolved.treeState());
        assertTrue(resolved.sourceTreeDigest().matches("[0-9a-f]{64}"));
        assertEquals(
            resolved.sourceTreeDigest(),
            ProvenanceSupport.digest(root, List.of(source.toFile()))
        );
    }

    @Test
    public void finalRejectsDirtyOrMismatchedCommit() throws Exception {
        Path root = Files.createTempDirectory("final provenance fixture");
        Path source = Files.writeString(root.resolve("source.txt"), "fixture\n");
        String finalCommit = "1111111111111111111111111111111111111111";
        assertThrows(IllegalStateException.class, () -> ProvenanceSupport.resolve(
            root, List.of(source.toFile()), "FINAL", finalCommit, "master", "modified", null, finalCommit
        ));
        assertThrows(IllegalStateException.class, () -> ProvenanceSupport.resolve(
            root, List.of(source.toFile()), "FINAL", finalCommit, "master", "clean", null,
            "2222222222222222222222222222222222222222"
        ));
    }

    @Test
    public void missingGitRequiresExplicitIdentity() throws Exception {
        Path root = Files.createTempDirectory("source zip provenance fixture");
        Path source = Files.writeString(root.resolve("source.txt"), "fixture\n");
        assertThrows(IllegalStateException.class, () -> ProvenanceSupport.resolve(
            root, List.of(source.toFile()), "VALIDATION", null, null, null, null, null
        ));
    }

    @Test
    public void wrongBranchIsRejected() throws Exception {
        Path root = Files.createTempDirectory("wrong branch provenance fixture");
        Path source = Files.writeString(root.resolve("source.txt"), "fixture\n");
        assertThrows(IllegalStateException.class, () -> ProvenanceSupport.resolve(
            root, List.of(source.toFile()), "VALIDATION", ProvenanceSupport.BASE_COMMIT, "feature/change007",
            "modified", null, null
        ));
    }
}
