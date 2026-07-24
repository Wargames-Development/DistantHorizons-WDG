# DistantHorizons-WDG

DistantHorizons-WDG is the **Wargames Development Group-maintained fork** of the Minecraft 1.7.10 backport of Distant Horizons.

- WDG repository: <https://github.com/Wargames-Development/DistantHorizons-WDG>
- Upstream 1.7.10 backport: <https://github.com/DarkShadow44/DistantHorizonsStandalone>
- Original Distant Horizons project: <https://gitlab.com/distant-horizons-team/distant-horizons>

Distant Horizons was created by James Seibel and its contributors. DarkShadow44 and the contributors to DistantHorizonsStandalone established and maintain the upstream 1.7.10 backport. Wargames Development Group maintains this fork while preserving the original LGPL-3.0 licensing, authorship, public mod identity, API, protocol, configuration, and world-data compatibility boundaries.

## What the mod does

Distant Horizons extends the visible world with Level of Detail terrain. Nearby Minecraft terrain remains fully rendered; progressively simplified distant terrain allows substantially longer effective view distances at a lower cost than rendering every distant chunk normally.

## Runtime contract

This repository targets:

- Minecraft 1.7.10;
- Forge 10.13.4.1614;
- the public mod ID `distanthorizons`;
- Java 21 for the **actual Minecraft game process**.

DistantHorizons-WDG is intentionally not converted to Java 8. Its production classes use Java 21 bytecode and modern Java APIs.

The intended WDG launch path is:

1. a launcher may initially enter through the legacy Java path;
2. `lwjgl3ify-wdg` installs or reuses a packaged Temurin Java 21 runtime;
3. `lwjgl3ify-wdg` relaunches the real Minecraft process under Java 21;
4. DistantHorizons-WDG loads inside that Java 21 process.

The automatic packaged-Java installer and relauncher belong to `lwjgl3ify-wdg`, not this repository. Change 005 established the production artifact foundation; Change 006 added deterministic Stage A/B/C client overlays; Change 007 turns that verified integration into a provenance-bearing WDG release candidate without changing the public mod identity or compatibility formats.

## Required dependencies

The 1.7.10 port requires these runtime mods:

- `lwjgl3ify` — the LWJGL 3 environment and WDG Java 21 bootstrap path;
- `gtnhlib` — GTNHLib APIs used directly by the implementation;
- `gtnhmixins` / UniMixins — the Mixin provider used by the early and normal Mixin configurations.

The default development dependency uses the reproducible upstream `lwjgl3ify` coordinate. Maintainers can opt into an exact local `lwjgl3ify-wdg` development JAR without publishing it first; see [COMPILING.md](COMPILING.md).

## Optional integrations

These are compatibility integrations, not mandatory dependencies:

- Angelica 2.1.54 or newer, including shader/render compatibility and an explicit runtime version gate;
- Hodgepodge;
- GregTech 5 Unofficial;
- RPLE;
- NotEnoughItems as an optional development convenience only.

The optional compatibility classes remain guarded by mod-presence checks. Angelica is compiled against 2.1.54 and accepted at runtime from 2.1.54 onward. Optional integrations are not published as required Maven or Forge dependencies.

## Client and server status

The release-candidate package is client-side. Do not copy DistantHorizons-WDG, the runtime-bearing lwjgl3ify-wdg JAR, or Angelica to a dedicated server solely for this feature. GTNHLib and UniMixins may still be independently required by other server mods and must be audited rather than removed blindly.

The supported Change 007 lwjgl3ify dependency is the single verified Change 005 production JAR. It embeds the four primary Java 21 runtimes for Linux x86_64, macOS AArch64, macOS x86_64, and Windows x86_64. Normal installation copies only the exact JAR set to `mods/`; there is no separate Java runtime ZIP or client overlay. Linux AArch64 and Windows AArch64 are optional manual extension assets.

Known upstream/runtime limitations can include stale LOD updates that recover after changing the render distance. Change 006 evidence established successful database migration, world save/reopen, renderer, and LOD activity on macOS; Change 007 still requires its own macOS, Windows, normal-modpack, and copied-server connection gates before commit.

## Development

- [SETUP.md](SETUP.md) — JDK and IntelliJ IDEA import setup.
- [COMPILING.md](COMPILING.md) — build, verification, artifact roles, local lwjgl3ify-wdg inputs, and troubleshooting.
- [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) — required, optional, development, and shadowed dependency classifications.
- [docs/COMBINED_CLIENT.md](docs/COMBINED_CLIENT.md) — exact-artifact Stage A/B/C packages, release-candidate packaging, verification, and external smoke procedures.
- [docs/RELEASE_CANDIDATE.md](docs/RELEASE_CANDIDATE.md) — two-phase provenance, stable-release gate, and acceptance status.

Ordinary Gradle client/server run tasks remain deliberately disabled. Change 007 constructs isolated production-like overlays and release assets from the exact reobfuscated Distant Horizons and `lwjgl3ify-wdg` JARs, GTNHLib 0.11.31, UniMixins All 0.1.23, Angelica 2.1.54, and the normalized packaged Java 21 bundle. Runtime acceptance remains an explicit external gate.

## Building

The repository uses the Gradle 9.4.0 wrapper. Gradle itself is selected through the checked-in Java 25 Adoptium daemon criteria; Distant Horizons source and tests compile with the Java 21 toolchain.

```bash
./gradlew --no-daemon verifyRepository
./gradlew --no-daemon test
./gradlew --no-daemon clean build
./gradlew --no-daemon verifyProductionModArtifact
./gradlew --no-daemon verifyPublishedDependencyMetadata
./gradlew --no-daemon explainCombinedClientInputs
```

With all explicit external artifact properties supplied, Change 007 preserves the Change 006 Stage A/B/C tasks and adds `packageWdgReleaseCandidate`, `verifyWdgReleaseCandidate`, `verifyWdgReleaseCandidateReproducibility`, `packageCurseForgeTestingProfile`, `auditWargamesModpackCompatibility`, and `auditDedicatedServer`.

The one distributable mod artifact is the unclassified output of `reobfJar`. The `-dev-preshadow`, `-dev`, sources, and API JARs are intermediate or development artifacts and must not be installed as the production mod.

## Licence and attribution

Distant Horizons source remains licensed under the GNU Lesser General Public License v3.0. Existing file-level copyright notices and upstream attribution are preserved. No statement in this README replaces the repository licence or the attribution in individual source files.

## WDG release-candidate status

Change 007 targets `3.0.4-b-wdg-rc.1`, not a public stable release. WDG builds embed deterministic provenance, submit development/RC warnings as separate single-line chat components, use managed-disabled upstream updating, and apply conservative defaults only to fresh profiles. See `docs/RELEASE_CANDIDATE.md` and the deployment documents under `docs/`.
