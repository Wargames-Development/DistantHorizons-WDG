# Development setup

## JDK roles

DistantHorizons-WDG deliberately has three explicit Java boundaries:

| Role | JDK |
| --- | --- |
| Start and run Gradle 9.4.0 | Java 25, Adoptium, selected by `gradle/gradle-daemon-jvm.properties` |
| Compile and test Distant Horizons | Java 21 toolchain |
| Run the real Minecraft process | Java 21 |

Java 8 is not an appropriate Gradle JVM, compiler toolchain, test JVM, or game JVM for this repository. Java 8 may exist only in the outer legacy launcher path before `lwjgl3ify-wdg` relaunches the actual game under Java 21.

## IntelliJ IDEA

1. Clone or extract the repository into a normal writable directory.
2. Open the repository root as a Gradle project; do not import individual modules manually.
3. Set **Gradle distribution** to the wrapper.
4. Set **Gradle JVM** to a Java 25 Adoptium JDK, or allow the checked-in daemon JVM criteria to provision/select it.
5. Use delegated Gradle build and test execution. This preserves RFG source generation, Mixin annotation processing, token replacement, resource expansion, shadowing, and reobfuscation.
6. Allow the Gradle import to complete before editing generated-source roots.

The Java 21 compiler/test toolchain is configured by the project and is distinct from IntelliJ's Gradle JVM. Seeing Java 25 start Gradle and Java 21 compile the mod is expected.

The direct `buildSrc` test build uses the matching daemon criteria in `buildSrc/gradle/gradle-daemon-jvm.properties`; it must also run its Gradle daemon on Java 25 rather than the shell's Java 8 launcher.

## Generated sources and resources

GTNHGradle/RetroFuturaGradle generate Minecraft sources and the Mixin refmap. `ModInfo.VERSION` reads the generated `com.seibel.distanthorizons.coreapi.Tags.VERSION` constant. GTNHGradle generates that class from the single `modVersion` property without mutating tracked source.

The production refmap is generated at:

```text
build/tmp/mixins/mixins.distanthorizons.refmap.json
```

Do not hand-edit or commit that generated file. The production verifier compares its exact bytes with the refmap packaged in the production JAR.

## Run tasks

`runClient`, `runServer`, `runClient17`, and `runServer17` remain disabled. They do not represent the intended packaged-Java runtime boundary for this fork. Change 006 packages isolated overlays but performs no Minecraft launch. Use `./gradlew explainProductionLaunchContract` and `./gradlew explainCombinedClientInputs` for the current boundary and explicit input contract. See `docs/COMBINED_CLIENT.md` before any CurseForge smoke.
