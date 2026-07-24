# Compiling and verification

## Normal commands

```bash
./gradlew --no-daemon --version
./gradlew --no-daemon spotlessApply
./gradlew --no-daemon spotlessCheck
./gradlew --no-daemon verifyRepository
./gradlew --no-daemon test
./gradlew --no-daemon checkstyleMain checkstyleTest
./gradlew --no-daemon clean build
./gradlew --no-daemon verifyProductionModArtifact
./gradlew --no-daemon verifyPublishedDependencyMetadata
```

`buildSrc` contains the focused foundation tests and can be exercised directly:

```bash
./gradlew --no-daemon -p buildSrc test
```

The standalone `buildSrc` project carries its own copy of the Java 25 Adoptium daemon criteria at `buildSrc/gradle/gradle-daemon-jvm.properties`. This prevents the direct test command from falling back to a Java 8 launcher JVM.

## Artifact roles

GTNHGradle's shadow pipeline has one authoritative order:

1. `jar` produces the `-dev-preshadow` intermediate;
2. `shadowJar` consumes that output and produces the `-dev` development artifact with internal Java libraries;
3. `reobfJar` consumes the shadow JAR and produces the unclassified production/reobfuscated mod JAR;
4. `sourcesJar` and `apiJar`, when present, are secondary development/publication artifacts.

`productionModArtifact` in `build.gradle.kts` is the provider for `reobfJar.archiveFile`. Verifiers and later packaging must use that provider and must never scan `build/libs`, select the newest JAR, or infer production identity solely from a filename.

Outputs are written below `build/`; mod JARs are normally under `build/libs/`, publication metadata under `build/publications/maven/`, and verification reports under `build/verification/`.

## Exact local lwjgl3ify-wdg inputs

The normal build uses the reproducible upstream development coordinate declared in `dependencies.gradle`.

To compile against an exact local deobfuscated/development artifact:

```bash
./gradlew --no-daemon clean build \
  -PwdgLwjgl3ifyDevJar="/absolute/path/with spaces/lwjgl3ify-3.0.28-dev.jar"
```

The file is added only to resolvable development classpaths. Published variants retain the normal module coordinate, so the absolute local path cannot leak into the POM or Gradle module metadata.

To compile the guarded Angelica compatibility adapter against the exact selected runtime JAR:

```bash
./gradlew --no-daemon clean build \
  -PwdgAngelicaJar="/absolute/path/to/angelica-2.1.54.jar"
```

The Angelica override accepts only the exact `angelica-2.1.54.jar` filename. It is compile-only and is not shaded, nested, or published as a mandatory dependency.

To verify the exact unclassified production/reobfuscated `lwjgl3ify-wdg` artifact:

```bash
./gradlew --no-daemon verifyWdgLwjgl3ifyCompatibility \
  -PwdgLwjgl3ifyProductionJar="/absolute/path/to/lwjgl3ify-3.0.28-master.5+d7e60f5a0d.jar"
```

The development and production properties are intentionally separate. A `-dev`, `-dev-preshadow`, sources, or API JAR is rejected by the production compatibility verifier.


## Change 006 suite preserved by Change 007

Build GTNHLib from its source ZIP first, using the supported `VERSION=0.11.31` override through `scripts/build-gtnhlib-0.11.31.sh`. Build and verify the exact lwjgl3ify Change 005 runtime-bearing production artifact separately. Then supply all explicit properties described in `docs/COMBINED_CLIENT.md`.

The preserved Stage A/B/C task sequence is:

```text
verifyGtnhLibArtifact
verifyAngelicaArtifact
verifyUniMixinsArtifact
verifyWdgLwjgl3ifyCompatibility
verifyRequiredRuntimeArtifacts
packageBootstrapSmokeClient
verifyBootstrapSmokeClient
packageDistantHorizonsSmokeClient
verifyDistantHorizonsSmokeClient
packageCombinedClient
verifyCombinedClientPackage
verifyCombinedClientReproducibility
```

Change 007 accepts only four explicit external mod inputs: the exact runtime-bearing lwjgl3ify Change 005 production JAR, GTNHLib 0.11.31, UniMixins All 0.1.23, and Angelica 2.1.54. The lwjgl3ify JAR embeds the four primary Temurin Java 21 archives for Linux x86_64, macOS AArch64, macOS x86_64, and Windows x86_64. No separate runtime ZIP or bundled-client overlay is accepted.

Generated Stage packages are written below `build/combined-client/packages`. They are clean overlays containing only the required mod JARs, README, and machine-readable manifest. Linux AArch64 and Windows AArch64 runtime extensions are optional manual assets and are not inputs to normal Stage or release-candidate packaging. Release-root and CurseForge testing-profile outputs are separate Change 007 tasks, and no runtime smoke result is implied by successful packaging alone.

## Optional development runtime integrations

Hodgepodge and NEI are not included in the default development runtime. They may be enabled explicitly without changing published metadata:

```bash
./gradlew --no-daemon tasks -PwdgEnableHodgepodgeRuntime=true
./gradlew --no-daemon tasks -PwdgEnableNeiRuntime=true
```

Ordinary Minecraft run tasks remain disabled in Change 007, so these flags primarily prepare controlled external integration work.

## Production artifact verification

`verifyProductionModArtifact` inspects the exact `reobfJar` output and checks metadata, version, Java 21 class files, early-loader manifest attributes, access transformer, both Mixin configurations, the exact generated refmap, slash-form DH mapping owners and obfuscated Minecraft targets, API classes, SQL migrations, shadowed libraries/native resources, archive collisions, and forbidden development or nested-mod payloads.

`verifyPublishedDependencyMetadata` generates and inspects the Maven POM and Gradle module metadata. It requires lwjgl3ify, GTNHLib, and UniMixins while rejecting Hodgepodge, NEI, Angelica, GregTech, RPLE, and local filesystem paths as mandatory published dependencies.

## Troubleshooting

### Wrong Gradle JVM

If Gradle reports an unsupported JVM or daemon mismatch, check `./gradlew --version` and IntelliJ's Gradle JVM. Gradle must start with Java 25 Adoptium. Do not point Gradle at Java 8 or Java 21 merely because the mod compiles with Java 21.

### Wrong compiler/test toolchain

Use `./gradlew javaToolchains`. Distant Horizons compile and test tasks must resolve Java 21. Production DH classes must report class-file major 65.

### Wrong lwjgl3ify artifact

Pass explicit absolute paths. The development input is normally a `-dev` artifact; the production verifier requires the exact unclassified reobfuscated artifact. Do not choose by modification time or directory order.

### Stale Mixin refmap

Run `clean build` and then `verifyProductionModArtifact`. The verifier reports the generated and packaged SHA-256 values when they differ. Do not weaken injection requirements or hand-copy a refmap.

### Native dependency problems

The production artifact must include SQLite JDBC and Zstandard JNI native resources exactly as provided by the shadowed dependencies. Inspect the production verifier output and archive listing before changing exclusions or dependency versions.

### Development versus production JAR confusion

Never install `-dev-preshadow`, `-dev`, `-sources`, or `-api` as the production mod. Use the unclassified `reobfJar` output identified by the verifier report.

## Release-candidate builds

The ordinary Change 007 candidate uses `wdgReleaseChannel=RELEASE_CANDIDATE`, version `3.0.4-b-wdg-rc.1` and `wdgProvenanceMode=VALIDATION`. Final assets must be rebuilt from a clean checkout with `-PwdgProvenanceMode=FINAL -PwdgExpectedCommit=<full-commit>`. Stable output is deliberately gated and is not produced by Change 007.
