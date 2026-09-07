# Distant Horizons WDG release candidate

## Status

`3.0.4-b-wdg-rc.1` is a Wargames Development Group **release candidate** based on upstream `3.0.4-b`. It is not a public stable release and must not be uploaded to a public release service until the later stable gate is deliberately satisfied.

The Change 007 validation workflow has two provenance modes:

- `VALIDATION`: source commit is the Change 007 base `170c809b2befbb3c54bd143ac68f8a9329e41f07`, source state is `modified`, and build source is `UNCOMMITTED_VALIDATION`.
- `FINAL`: source commit is the explicitly supplied clean Change 007 commit, source state is `clean`, and build source is `CLEAN_GIT_CHECKOUT`.

An uncommitted validation archive is never a final release asset. After commit and push, rebuild from a clean checkout with `-PwdgProvenanceMode=FINAL -PwdgExpectedCommit=<full-change-007-commit>`.

## Version and compatibility contracts

- Mod version: `3.0.4-b-wdg-rc.1`
- Minecraft: `1.7.10`
- Forge: `10.13.4.1614`
- Java launcher parent: Java 8
- Actual game process: packaged Temurin Java `21.0.11`
- Java class-file target: 65
- LWJGL: `3.4.2`
- API version: unchanged (`7.0.0`)
- network protocol: unchanged (`15`)
- config version: unchanged (`4`)
- SQL migrations, database schema, LOD storage paths and formats: unchanged

`build_info.json`, `ModInfo.VERSION`, `mcmod.info`, the JAR filename, JAR manifest, package manifest, testing-profile version and startup log must agree exactly.

## Warning rendering

Development and release-candidate builds produce immutable ordered warning lines. Each line is submitted in a separate Minecraft 1.7.10 `ChatComponentText`; CR, LF, escaped separators and literal `LF` separator tokens are rejected. Stable builds submit no unstable-build warning.

## Embedded packaged-Java distribution

The exact supported lwjgl3ify Change 005 production JAR is a one-JAR runtime distribution. It embeds four primary Temurin Java 21 archives for Linux x86_64, macOS AArch64, macOS x86_64, and Windows x86_64. Stage A/B/C, the release root, and the CurseForge testing profile contain no separate runtime ZIP or bundled-client overlay. Linux AArch64 and Windows AArch64 extension archives are optional manual additions.

## Managed updater

WDG builds use provenance policy `MANAGED_DISABLED`. The policy gate is evaluated before Modrinth, GitLab, download, checksum, update-directory or shutdown-deletion code. Existing `enableAutoUpdater=true` values are retained in the user's file but have no effective network/update behaviour in a WDG-managed artifact.

## Future stable gate

A future stable build requires all of the following together: channel `STABLE`, an explicit confirmation, clean checkout, exact expected tag, exact expected full commit, no dirty suffix, and a completed acceptance manifest. Editing one Gradle property cannot create a stable artifact. Change 007 does not activate or satisfy this gate.
