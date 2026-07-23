# Dependency classification

## Required runtime and implementation mods

| Dependency | Public mod ID | Build role | Publication role |
| --- | --- | --- | --- |
| lwjgl3ify | `lwjgl3ify` | `implementation`; provides the LWJGL 3 environment and intended WDG Java 21 bootstrap | Required runtime dependency; never shaded or nested |
| GTNHLib | `gtnhlib` | `implementation`; source directly uses GTNHLib classes | Required runtime dependency; never shaded or nested |
| UniMixins / GTNHMixins | `gtnhmixins` | Added by the GTNHGradle Mixin convention for annotation processing, compile, and runtime | Required Mixin provider; do not add a second provider |

The WDG fork does not create a new public mod ID such as `lwjgl3ify-wdg`. Local WDG artifacts retain the public `lwjgl3ify` identity.

## Optional integration compile-only dependencies

| Dependency | Reason |
| --- | --- |
| Hodgepodge | `SimulationDistanceHelper` integration is guarded by Hodgepodge presence |
| Angelica 2.1.54 or newer | Optional shader/render compatibility, compiled and runtime-gated against the selected 2.1.54 compatibility floor |
| GregTech 5 Unofficial | Compatibility wrapper is entered only after a presence/class check |
| RPLE | Optional compatibility wrapper is entered only after a presence check |

These are `compileOnly`; they are not mandatory in Forge metadata, Maven POM, Gradle module metadata, CurseForge relations, or Modrinth relations.

## Development-only dependencies

NEI is an opt-in `runtimeOnlyNonPublishable` convenience. Hodgepodge can also be added to a controlled development runtime with `wdgEnableHodgepodgeRuntime=true`. Neither is published.

An explicit `wdgLwjgl3ifyDevJar` is a local, deobfuscated development input. It replaces the default module only on resolvable classpaths and does not alter published metadata. `wdgAngelicaJar` similarly selects the exact `angelica-2.1.54.jar` as a compile-only compatibility input without publishing or shading it.

## Shadowed implementation libraries

The production Distant Horizons JAR contains these private Java libraries:

- NightConfig TOML and JSON;
- LZ4 Java;
- XZ;
- SQLite JDBC, including its native resources;
- Zstandard JNI, including its native resources.

They are implementation details, not Forge mods. `shadowJar` packages them and `reobfJar` produces the distributable Minecraft artifact. Their versions remain unchanged in Change 006.

## Test-only dependencies

The focused build contract tests live in `buildSrc` and use JUnit 4. They create small temporary fixture JARs; no Minecraft, native, lwjgl3ify, runtime bundle, or large third-party JAR is checked into test resources.


## Change 006 package inputs

Combined-client packaging consumes exact external files through explicit Gradle properties. GTNHLib is built from the supplied 0.11.31 source ZIP with `VERSION=0.11.31`; only its verified production `reobfJar` is packaged. Angelica 2.1.54 and UniMixins All 0.1.23 are verified by exact size, SHA-256, metadata, core-plugin/Mixin structure, bytecode layout, and forbidden-content checks. The normalized six-platform Temurin Java 21 bundle is verified separately and remains under `lwjgl3ify/runtime`.

No external artifact path enters publication metadata, and no external mod or runtime archive is shaded or nested into the Distant Horizons JAR.
