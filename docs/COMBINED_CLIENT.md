# Change 006 combined-client integration

Change 006 adds an isolated, reproducible packaging and verification boundary around the exact production Distant Horizons, lwjgl3ify-wdg, GTNHLib, UniMixins, Angelica, and packaged Temurin Java 21 artifacts. It does not alter rendering, LOD generation, networking, database schemas, SQL migrations, API versions, config formats, world paths, or Mixin injection requirements.

Runtime smoke is deliberately staged and is **not considered passed until a user completes the CurseForge checks below**.

## Component contract

| Component | Required identity | Package role |
| --- | --- | --- |
| DistantHorizons-WDG | Change 005 commit `1bcabae75b3ae3160995355e1c46d94659146d0e`; production `reobfJar`; Java major 65 | Stage B and C |
| lwjgl3ify-wdg | Change 004 commit `7500f19e88a47e6ecc587f33789766bbac365d19`; production `reobfJar` | Stage A, B, and C |
| GTNHLib | `0.11.31`, built from the supplied source ZIP with `VERSION=0.11.31` | Stage A, B, and C |
| UniMixins All | `0.1.23`, including the `gtnhmixins` module | Stage A, B, and C |
| Angelica | exact `2.1.54` production JAR | Stage C only |
| Temurin runtime bundle | normalized six-platform Java 21.0.11+10-LTS output from lwjgl3ify-wdg | Stage A, B, and C |

The Java runtime archives remain under `lwjgl3ify/runtime`. They are never embedded in either mod JAR and are never placed beneath `mods`.

## External artifact properties

Combined-client tasks use only explicit paths. Absolute paths and paths containing spaces are supported. Relative paths are resolved from the DistantHorizons-WDG project directory. Nothing scans Downloads, chooses the newest file, scans `build/libs`, or silently downloads alternatives.

```text
wdgLwjgl3ifyProductionJar
wdgLwjgl3ifyBundledClientPackage
wdgLwjgl3ifyRuntimeBundle
wdgAngelicaJar
wdgUniMixinsJar
wdgGtnhLibJar
```

The helper source path may also be documented as:

```text
wdgGtnhLibSourceZip
```

The ordinary Distant Horizons `build` remains independent of these package inputs.

## Build exact lwjgl3ify-wdg outputs

From the clean Change 004 repository, use the supplied `Required Java Packages.zip` as `wdgJavaRuntimeBundle` and run the repository's established tasks. The authoritative outputs are:

```text
build/libs/<unclassified production reobfJar>
build/runtime-packages/lwjgl3ify-wdg-java21-runtimes.zip
build/distributions/lwjgl3ify-wdg-<version>-client-with-java21.zip
build/verification/production-mod-artifact.properties
```

Required checks include `verifyRepository`, `verifyProductionModArtifact`, `verifyJavaRuntimeBundle`, `packageJavaRuntimeBundle`, `verifyJavaRuntimeInstallation`, `verifyAutomaticJavaRuntime`, `packageBundledJavaClient`, and `verifyBundledJavaClientPackage`.

## Build exact GTNHLib 0.11.31

The uploaded GTNHLib file is source, not a mod JAR. Its Gradle convention derives the release version from Git metadata, which is absent from a source ZIP. The supported build-time override must therefore be used exactly:

```text
VERSION=0.11.31
```

Do not initialize a fake Git repository or edit generated version source. Run:

```text
scripts/build-gtnhlib-0.11.31.sh <source-zip> <output-directory-beneath-build>
```

The helper verifies the source SHA-256, rejects unsafe archive paths, extracts to a unique temporary directory, restores `gradlew`, runs Spotless/tests/`reobfJar`, reads the exact `reobfJar` output through an injected task-output contract, verifies the production JAR, copies only `gtnhlib-0.11.31.jar`, emits JSON, and removes temporary source on both success and failure.

The verifier requires Java 8-compatible base classes, Java 17 entries beneath `META-INF/versions/17`, `Multi-Release: true`, `Tags.VERSION=0.11.31` resolved through the generated token's inlined production consumers (the generated `Tags.class` is compile-time-only and is not required inside the final JAR), the GTNHLib coremod, access transformer, Mixin metadata, and RFB metadata. `NO-GIT-TAG-SET`, `0.0.0`, `dirty`, classified outputs, source files, tests, nested mod JARs, and profile data are rejected.

## Distant Horizons build and verification

Run formatting and the normal static validation first. The production mod remains the unclassified `reobfJar` output. `verifyProductionModArtifact` must still pass and must report Java class-file major 65, `Lwjgl3ify-Aware: true`, production Mixin refmap data, native SQLite/Zstandard resources, and the unchanged migration contract.

## Artifact verification tasks

```text
verifyGtnhLibArtifact
verifyAngelicaArtifact
verifyUniMixinsArtifact
verifyNormalizedRuntimeBundle
verifyWdgLwjgl3ifyCompatibility
verifyRequiredRuntimeArtifacts
```

`verifyRequiredRuntimeArtifacts` also verifies the existing lwjgl3ify bundled-Java overlay against the exact production lwjgl3ify JAR and exact normalized runtime bundle.

## Staged package roles

### Stage A: packaged Java bootstrap

Task: `packageBootstrapSmokeClient`  
Verifier: `verifyBootstrapSmokeClient`

Contains lwjgl3ify, GTNHLib, UniMixins/GTNHMixins, and the normalized runtime bundle. It excludes Distant Horizons and Angelica. Its purpose is to prove the Java 8 parent to packaged Java 21 child handoff.

### Stage B: Distant Horizons required stack

Task: `packageDistantHorizonsSmokeClient`  
Verifier: `verifyDistantHorizonsSmokeClient`

Contains Stage A plus the exact Distant Horizons production JAR. It excludes Angelica. Its purpose is to prove the required runtime stack before shader/render integration.

### Stage C: full combined client

Task: `packageCombinedClient`  
Verifier: `verifyCombinedClientPackage`

Contains Stage B plus exact Angelica 2.1.54. It is the final main-menu and disposable-world LOD smoke package.

Each ZIP has one deterministic root, a README, `wdg-combined-client-manifest.json`, exact mod bytes, and the runtime bundle under `lwjgl3ify/runtime`. Fixed ZIP timestamps, lexical entry ordering, stable JSON ordering, and stored external bytes provide deterministic identity. `verifyCombinedClientReproducibility` creates Stage C twice and compares exact bytes and SHA-256 values without rebuilding external repositories.

## Final Stage C layout

```text
DistantHorizons-WDG-Combined-Client/
├── README.txt
├── wdg-combined-client-manifest.json
├── mods/
│   ├── <exact Distant Horizons production JAR>
│   ├── <exact lwjgl3ify production JAR>
│   ├── angelica-2.1.54.jar
│   ├── gtnhlib-0.11.31.jar
│   └── +unimixins-all-1.7.10-0.1.23.jar
└── lwjgl3ify/
    └── runtime/
        └── lwjgl3ify-wdg-java21-runtimes.zip
```

The package manifest records roles, original and packaged filenames, mod IDs, versions, sizes, SHA-256 values, source types, required/optional status, commits, Minecraft/Forge versions, package root, runtime bundle identity, and six supported platforms. It contains no absolute paths, usernames, home directories, timestamps used as identity, account data, or launcher tokens.

## CurseForge disposable-profile smoke

Create a new custom profile through the current CurseForge interface using Minecraft `1.7.10` and Forge `10.13.4.1614`. Open the profile's folder from CurseForge rather than guessing its path. Start with the launcher's Java 8 configuration. Do not copy any existing mods, configs, worlds, options, logs, shader packs, resource packs, or account files.

Apply one clean overlay ZIP to one clean disposable profile. Do not use `--delete`. Prefer separate profile copies for Stage A, Stage B, and Stage C so no individual JAR deletion is required.

### Stage A acceptance

Confirm the launcher initially starts through Java 8, lwjgl3ify resolves `macos-aarch64` on native Apple Silicon, Temurin Java 21.0.11 is installed or reused, the Java 21 child starts, the Java 8 parent exits cleanly, only one Minecraft client remains, the main menu opens, lwjgl3ify/GTNHLib/UniMixins/GTNHMixins are recognized, and normal shutdown returns zero. Do not proceed to Stage B until the evidence is reviewed.

### Stage B acceptance

Use a fresh profile and Stage B. Confirm all Stage A behavior, Distant Horizons recognition and exact version, no missing GTNHLib or Mixin classes, no critical Mixin failure, no SQLite/Zstandard native extraction failure, and a successful main menu. Angelica must remain absent.

### Stage C main-menu acceptance

Use a fresh profile and Stage C. Confirm Distant Horizons, lwjgl3ify, Angelica 2.1.54, GTNHLib 0.11.31, UniMixins 0.1.23, and GTNHMixins are recognized; Java 21 and LWJGL 3 are active; and there are no critical Mixin, duplicate-mod, native-library, or relaunch-loop failures.

### Disposable world and reopen

Create a new single-player world named `WDG DH Change 006 Smoke`. Do not use a valuable world. Confirm world creation, entry, Distant Horizons storage/database initialization, renderer initialization, chunk loading, initial LOD generation/rendering, movement, save-and-return, and reopening the same world. Slow first-time LOD generation alone is not failure. Stop and return evidence for a bounded corrective patch if any critical crash, SQL migration failure, native failure, repeated relaunch, or profile corruption occurs.

## Relevant logs

Return only relevant files where present:

```text
logs/latest.log
logs/fml-client-latest.log
lwjgl3ify relauncher log
Distant Horizons log
crash-reports/<relevant report>
```

Also return package verification output, the mod-list section, Java version evidence, managed-runtime install/reuse lines, and Distant Horizons database/renderer initialization lines. Review logs for personal paths before uploading. Never upload launcher account files, authentication databases, access tokens, Microsoft account data, or an entire launcher installation.

## Cleanup

Generated source-build staging, packages, verification reports, Gradle caches, and build output live beneath `build`, `.gradle`, `buildSrc/build`, or `buildSrc/.gradle`. Cleanup may remove those paths while preserving `.git`, tracked source, IntelliJ state, and local `run` data by default. User worlds and configs must remain untouched.

## Deferred work

Dedicated-server stability, long-running memory behavior, server runtime smoke, gameplay behavior, rendering changes, LOD algorithms, database changes, networking changes, and configuration changes remain outside Change 006.

### GTNHLib and Angelica compatibility

The combined client pins GTNHLib 0.11.31. Angelica 2.1.54 requires GTNHLib 0.10.0 or newer; older GTNHLib artifacts must be rejected before packaging.

### GTNHLib nested loader contract

GTNHLib 0.11.31 intentionally contains exactly one nested loader artifact:

- path: `fplib_deploader.jar`
- size: `78,531` bytes
- SHA-256: `272234bfca7a9c7b75ecbcf90ad6dd0fb63dd3d7cb45011061387558d317cae4`

The production verifier rejects a missing, relocated, altered, or additional
nested JAR. Other runtime artifacts continue to reject all nested JAR content.

## Updating combined-client dependencies

Use `scripts/update-combined-client-artifact.sh` for routine GTNHLib,
Angelica and UniMixins releases.

The full procedure and example commands are documented in
`docs/UPDATING_COMBINED_CLIENT_ARTIFACTS.md`. Structural changes such as new
mod IDs or changed nested-JAR layouts are rejected for manual review.

