package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;

public final class StableReleaseGate {

    private StableReleaseGate() {}

    public static void verify(
        String releaseChannel,
        boolean explicitConfirmation,
        String treeState,
        String expectedTag,
        String actualTag,
        String expectedCommit,
        String actualCommit,
        File acceptanceManifest
    ) {
        if (!"STABLE".equals(releaseChannel)) {
            throw new IllegalStateException("Stable release gate requires releaseChannel=STABLE");
        }
        if (!explicitConfirmation) {
            throw new IllegalStateException("Stable release gate requires explicit stable confirmation");
        }
        if (!"clean".equals(treeState)) {
            throw new IllegalStateException("Stable release gate requires a clean checkout");
        }
        requireEqual("Git tag", expectedTag, actualTag);
        requireEqual("Git commit", expectedCommit, actualCommit);
        if (actualCommit == null || !actualCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalStateException("Stable release gate requires an exact full Git commit");
        }
        if (acceptanceManifest == null || !acceptanceManifest.isFile() || !acceptanceManifest.canRead()) {
            throw new IllegalStateException("Stable release gate requires a completed acceptance manifest");
        }
    }

    private static void requireEqual(String label, String expected, String actual) {
        if (expected == null || expected.isBlank() || !expected.equals(actual)) {
            throw new IllegalStateException(label + " mismatch: expected=" + expected + ", actual=" + actual);
        }
    }
}
