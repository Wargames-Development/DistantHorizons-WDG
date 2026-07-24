package com.wargamesdevelopment.distanthorizons.gradle;

import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class StableReleaseGateTest {

    @Test
    public void singlePropertyCannotCreateStableRelease() throws Exception {
        Path manifest = Files.writeString(Files.createTempFile("acceptance", ".json"), "{}\n");
        String commit = "1111111111111111111111111111111111111111";
        assertThrows(IllegalStateException.class, () -> StableReleaseGate.verify(
            "STABLE", false, "clean", "v3.0.4-b-wdg.1", "v3.0.4-b-wdg.1",
            commit, commit, manifest.toFile()
        ));
    }

    @Test
    public void completeFutureGateCanPass() throws Exception {
        Path manifest = Files.writeString(Files.createTempFile("acceptance", ".json"), "{}\n");
        String commit = "1111111111111111111111111111111111111111";
        StableReleaseGate.verify(
            "STABLE", true, "clean", "v3.0.4-b-wdg.1", "v3.0.4-b-wdg.1",
            commit, commit, manifest.toFile()
        );
    }
}
