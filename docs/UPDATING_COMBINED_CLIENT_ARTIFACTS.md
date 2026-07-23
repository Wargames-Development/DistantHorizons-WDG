# Updating combined-client artifacts

The production identities for GTNHLib, Angelica and UniMixins are maintained
through RuntimeArtifactVerifier.java.

Do not manually search through the repository to change every version,
filename, size and SHA-256 value. Use the supplied update helper.

Angelica:

    COMMANDS_DIR="$HOME/Downloads/DistantHorizons-WDG-change-006-correction-8-commands" \
    bash scripts/update-combined-client-artifact.sh \
        angelica \
        "$HOME/Downloads/angelica-NEW_VERSION.jar"

UniMixins All:

    COMMANDS_DIR="$HOME/Downloads/DistantHorizons-WDG-change-006-correction-8-commands" \
    bash scripts/update-combined-client-artifact.sh \
        unimixins \
        "$HOME/Downloads/+unimixins-all-1.7.10-NEW_VERSION.jar"

GTNHLib requires both the production JAR and its matching source ZIP:

    COMMANDS_DIR="$HOME/Downloads/DistantHorizons-WDG-change-006-correction-8-commands" \
    bash scripts/update-combined-client-artifact.sh \
        gtnhlib \
        "$HOME/Downloads/gtnhlib-NEW_VERSION.jar" \
        "$HOME/Downloads/GTNHLib-NEW_VERSION.zip"

The updater performs the following work:

- reads the artifact version and mod identities from mcmod.info;
- calculates the exact size and SHA-256;
- checks that the expected mod-ID structure remains unchanged;
- checks the permitted nested-JAR structure;
- updates the verifier catalogue and repository references;
- updates the active Change 006 validation command bundle;
- renames the version-specific GTNHLib build script when required;
- runs formatting, buildSrc tests and repository verification.

The updater deliberately refuses an automatic update when mod IDs or the
nested-JAR structure change. Those changes require a compatibility review
rather than an automatic weakening of the verifier.
