import com.wargamesdevelopment.distanthorizons.gradle.CombinedClientSupport
import com.wargamesdevelopment.distanthorizons.gradle.PackageCombinedClientTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyCombinedClientPackageTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyCombinedClientReproducibilityTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyRequiredRuntimeArtifactsTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyRuntimeArtifactTask
import com.wargamesdevelopment.distanthorizons.gradle.FoundationSupport
import com.wargamesdevelopment.distanthorizons.gradle.VerifyProductionModArtifactTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyPublishedDependencyMetadataTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyRepositoryTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyWdgLwjgl3ifyCompatibilityTask
import org.gradle.api.tasks.PathSensitivity
import java.io.File

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val unifiedModVersion = providers.gradleProperty("modVersion")
    .map(FoundationSupport::validateVersion)
    .get()
version = unifiedModVersion
extensions.extraProperties["modVersion"] = unifiedModVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    testing {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}

minecraft {
    javaCompatibilityVersion = 21

    // Ordinary development run tasks remain disabled. Change 006 will establish an isolated,
    // production-like launch using exact reobfuscated DH and lwjgl3ify-wdg artifacts plus Java 21.
}

for (jarTask in listOf(tasks.jar, tasks.shadowJar, tasks.sourcesJar)) {
    jarTask.configure {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        manifest {
            attributes(
                "Lwjgl3ify-Aware" to true,
                "Implementation-Title" to "Distant Horizons",
                "Implementation-Version" to unifiedModVersion,
                "Implementation-Vendor" to "Wargames Development Group",
            )
        }
    }
}

tasks.runClient { enabled = false }
tasks.runServer { enabled = false }
tasks.runClient17 { enabled = false }
tasks.runServer17 { enabled = false }

val generatedMixinRefmap = layout.buildDirectory.file("tmp/mixins/${FoundationSupport.REFMAP}")
val productionModArtifact = tasks.reobfJar.flatMap { it.archiveFile }

// Verification runs after a clean build and intentionally consumes the exact canonical output
// path without carrying reobfJar's full RFG task graph into configuration-cache verification.
val productionModArtifactForVerification =
    layout.buildDirectory.file("libs/distanthorizons-$unifiedModVersion.jar")

// RFG copies the annotation-processor refmap during processResources. Declare it as an explicit
// semantic input so shadow/reobf cannot reuse an artifact after the generated mappings change.
tasks.processResources {
    inputs.file(generatedMixinRefmap)
        .withPropertyName("generatedMixinRefmap")
        .withPathSensitivity(PathSensitivity.NONE)
}
tasks.shadowJar {
    dependsOn(tasks.processResources)
    inputs.file(generatedMixinRefmap)
        .withPropertyName("generatedMixinRefmap")
        .withPathSensitivity(PathSensitivity.NONE)
}
tasks.reobfJar {
    dependsOn(tasks.processResources)
    inputs.file(generatedMixinRefmap)
        .withPropertyName("generatedMixinRefmap")
        .withPathSensitivity(PathSensitivity.NONE)
}

val verifyRepository = tasks.register<VerifyRepositoryTask>("verifyRepository") {
    group = "verification"
    description = "Verifies stable DistantHorizons-WDG repository invariants without launching Minecraft."
    repositoryDirectory.set(layout.projectDirectory)
    repositoryFiles.from(
        fileTree(projectDir) {
            exclude(
                ".git/**",
                ".gradle/**",
                "build/**",
                "buildSrc/.gradle/**",
                "buildSrc/build/**",
                "run/**",
                "eclipse/**",
                ".idea/**",
                ".vscode/**",
                "validation-logs/**",
            )
        },
    )
    expectedVersion.set(unifiedModVersion)
    reportFile.set(layout.buildDirectory.file("verification/repository.properties"))
}

tasks.named("check") {
    dependsOn(verifyRepository)
}

val verifyProductionModArtifact =
    tasks.register<VerifyProductionModArtifactTask>("verifyProductionModArtifact") {
        group = "verification"
        description = "Verifies the exact reobfuscated, shadowed Distant Horizons production JAR."
        artifactFile.set(productionModArtifactForVerification)
        generatedRefmap.set(generatedMixinRefmap)
        expectedVersion.set(unifiedModVersion)
        reportFile.set(layout.buildDirectory.file("verification/production-mod-artifact.properties"))
    }

fun externalArtifact(name: String) = layout.file(
    providers.gradleProperty(name).map { configuredPath ->
        val configured = File(configuredPath)
        if (configured.isAbsolute) configured else layout.projectDirectory.file(configuredPath).asFile
    },
)

val localLwjgl3ifyProductionArtifact = externalArtifact("wdgLwjgl3ifyProductionJar")
val localLwjgl3ifyBundledClientPackage = externalArtifact("wdgLwjgl3ifyBundledClientPackage")
val localLwjgl3ifyRuntimeBundle = externalArtifact("wdgLwjgl3ifyRuntimeBundle")
val localAngelicaArtifact = externalArtifact("wdgAngelicaJar")
val localUniMixinsArtifact = externalArtifact("wdgUniMixinsJar")
val localGtnhLibArtifact = externalArtifact("wdgGtnhLibJar")
val localGtnhLibSourceZip = providers.gradleProperty("wdgGtnhLibSourceZip")

tasks.register<VerifyWdgLwjgl3ifyCompatibilityTask>("verifyWdgLwjgl3ifyCompatibility") {
    group = "verification"
    description = "Checks an explicitly supplied lwjgl3ify-wdg production reobf JAR against the Change 004 contract."
    productionArtifact.set(localLwjgl3ifyProductionArtifact)
    reportFile.set(layout.buildDirectory.file("verification/lwjgl3ify-wdg-compatibility.properties"))
}

tasks.register<VerifyPublishedDependencyMetadataTask>("verifyPublishedDependencyMetadata") {
    group = "verification"
    description = "Verifies generated Maven and Gradle metadata keep optional integrations non-mandatory."
    dependsOn("generatePomFileForMavenPublication", "generateMetadataFileForMavenPublication")
    pomFile.set(layout.buildDirectory.file("publications/maven/pom-default.xml"))
    moduleMetadataFile.set(layout.buildDirectory.file("publications/maven/module.json"))
    expectedVersion.set(unifiedModVersion)
    reportFile.set(layout.buildDirectory.file("verification/published-dependencies.properties"))
}


val verifyGtnhLibArtifact = tasks.register<VerifyRuntimeArtifactTask>("verifyGtnhLibArtifact") {
    group = "verification"
    description = "Verifies the exact GTNHLib 0.11.31 production JAR built from the supplied source ZIP."
    artifactKind.set("gtnhlib")
    propertyHint.set("wdgGtnhLibJar")
    artifactFile.set(localGtnhLibArtifact)
    reportFile.set(layout.buildDirectory.file("verification/gtnhlib-artifact.properties"))
}

val verifyAngelicaArtifact = tasks.register<VerifyRuntimeArtifactTask>("verifyAngelicaArtifact") {
    group = "verification"
    description = "Verifies the exact Angelica 2.1.54 production composite JAR."
    artifactKind.set("angelica")
    propertyHint.set("wdgAngelicaJar")
    artifactFile.set(localAngelicaArtifact)
    reportFile.set(layout.buildDirectory.file("verification/angelica-artifact.properties"))
}

val verifyUniMixinsArtifact = tasks.register<VerifyRuntimeArtifactTask>("verifyUniMixinsArtifact") {
    group = "verification"
    description = "Verifies UniMixins All 0.1.23 and its embedded GTNHMixins module."
    artifactKind.set("unimixins")
    propertyHint.set("wdgUniMixinsJar")
    artifactFile.set(localUniMixinsArtifact)
    reportFile.set(layout.buildDirectory.file("verification/unimixins-artifact.properties"))
}

val verifyNormalizedRuntimeBundle = tasks.register<VerifyRuntimeArtifactTask>("verifyNormalizedRuntimeBundle") {
    group = "verification"
    description = "Verifies the exact normalized six-platform lwjgl3ify Java 21 runtime bundle."
    artifactKind.set("runtime-bundle")
    propertyHint.set("wdgLwjgl3ifyRuntimeBundle")
    artifactFile.set(localLwjgl3ifyRuntimeBundle)
    reportFile.set(layout.buildDirectory.file("verification/java21-runtime-bundle.properties"))
}

val verifyRequiredRuntimeArtifacts =
    tasks.register<VerifyRequiredRuntimeArtifactsTask>("verifyRequiredRuntimeArtifacts") {
        group = "verification"
        description = "Deeply verifies every exact external and project artifact used by Change 006."
        dependsOn(
            verifyProductionModArtifact,
            verifyGtnhLibArtifact,
            verifyAngelicaArtifact,
            verifyUniMixinsArtifact,
            verifyNormalizedRuntimeBundle,
            "verifyWdgLwjgl3ifyCompatibility",
        )
        distantHorizonsJar.set(productionModArtifactForVerification)
        generatedRefmap.set(generatedMixinRefmap)
        distantHorizonsVersion.set(unifiedModVersion)
        lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
        lwjgl3ifyBundledClientPackage.set(localLwjgl3ifyBundledClientPackage)
        runtimeBundle.set(localLwjgl3ifyRuntimeBundle)
        angelicaJar.set(localAngelicaArtifact)
        uniMixinsJar.set(localUniMixinsArtifact)
        gtnhLibJar.set(localGtnhLibArtifact)
        reportFile.set(layout.buildDirectory.file("verification/required-runtime-artifacts.properties"))
    }

fun PackageCombinedClientTask.configureCombinedInputs(
    type: String,
    root: String,
    includeDh: Boolean,
    includeShaders: Boolean,
) {
    group = "distribution"
    packageType.set(type)
    rootDirectory.set(root)
    includeDistantHorizons.set(includeDh)
    includeAngelica.set(includeShaders)
    distantHorizonsVersion.set(unifiedModVersion)
    if (includeDh) {
        distantHorizonsJar.set(productionModArtifactForVerification)
        generatedRefmap.set(generatedMixinRefmap)
    }
    lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
    gtnhLibJar.set(localGtnhLibArtifact)
    uniMixinsJar.set(localUniMixinsArtifact)
    if (includeShaders) {
        angelicaJar.set(localAngelicaArtifact)
    }
    runtimeBundle.set(localLwjgl3ifyRuntimeBundle)
}

fun VerifyCombinedClientPackageTask.configureCombinedVerification(
    packageTask: org.gradle.api.tasks.TaskProvider<PackageCombinedClientTask>,
    type: String,
    root: String,
    includeDh: Boolean,
    includeShaders: Boolean,
) {
    group = "verification"
    dependsOn(packageTask)
    packageType.set(type)
    rootDirectory.set(root)
    includeDistantHorizons.set(includeDh)
    includeAngelica.set(includeShaders)
    distantHorizonsVersion.set(unifiedModVersion)
    packageFile.set(packageTask.flatMap { it.outputFile })
    if (includeDh) {
        distantHorizonsJar.set(productionModArtifactForVerification)
        generatedRefmap.set(generatedMixinRefmap)
    }
    lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
    gtnhLibJar.set(localGtnhLibArtifact)
    uniMixinsJar.set(localUniMixinsArtifact)
    if (includeShaders) {
        angelicaJar.set(localAngelicaArtifact)
    }
    runtimeBundle.set(localLwjgl3ifyRuntimeBundle)
}

val packageBootstrapSmokeClient = tasks.register<PackageCombinedClientTask>("packageBootstrapSmokeClient") {
    description = "Packages Stage A: lwjgl3ify, GTNHLib, UniMixins/GTNHMixins, and packaged Java 21."
    dependsOn(
        verifyGtnhLibArtifact,
        verifyUniMixinsArtifact,
        verifyNormalizedRuntimeBundle,
        "verifyWdgLwjgl3ifyCompatibility",
    )
    configureCombinedInputs(
        "bootstrap-smoke",
        "DistantHorizons-WDG-Bootstrap-Smoke",
        includeDh = false,
        includeShaders = false,
    )
    outputFile.set(layout.buildDirectory.file("combined-client/packages/DistantHorizons-WDG-bootstrap-smoke.zip"))
    reportFile.set(layout.buildDirectory.file("verification/bootstrap-smoke-package.properties"))
}

val verifyBootstrapSmokeClient =
    tasks.register<VerifyCombinedClientPackageTask>("verifyBootstrapSmokeClient") {
        description = "Verifies the deterministic Stage A packaged-Java bootstrap overlay."
        configureCombinedVerification(
            packageBootstrapSmokeClient,
            "bootstrap-smoke",
            "DistantHorizons-WDG-Bootstrap-Smoke",
            includeDh = false,
            includeShaders = false,
        )
        reportFile.set(layout.buildDirectory.file("verification/bootstrap-smoke-verification.properties"))
    }

val packageDistantHorizonsSmokeClient =
    tasks.register<PackageCombinedClientTask>("packageDistantHorizonsSmokeClient") {
        description = "Packages Stage B: the required Distant Horizons stack without Angelica."
        dependsOn(
            verifyProductionModArtifact,
            verifyGtnhLibArtifact,
            verifyUniMixinsArtifact,
            verifyNormalizedRuntimeBundle,
            "verifyWdgLwjgl3ifyCompatibility",
        )
        configureCombinedInputs(
            "distant-horizons-required-smoke",
            "DistantHorizons-WDG-DH-Smoke",
            includeDh = true,
            includeShaders = false,
        )
        outputFile.set(layout.buildDirectory.file("combined-client/packages/DistantHorizons-WDG-dh-required-smoke.zip"))
        reportFile.set(layout.buildDirectory.file("verification/dh-required-smoke-package.properties"))
    }

val verifyDistantHorizonsSmokeClient =
    tasks.register<VerifyCombinedClientPackageTask>("verifyDistantHorizonsSmokeClient") {
        description = "Verifies the deterministic Stage B Distant Horizons required-stack overlay."
        configureCombinedVerification(
            packageDistantHorizonsSmokeClient,
            "distant-horizons-required-smoke",
            "DistantHorizons-WDG-DH-Smoke",
            includeDh = true,
            includeShaders = false,
        )
        reportFile.set(layout.buildDirectory.file("verification/dh-required-smoke-verification.properties"))
    }

val packageCombinedClient = tasks.register<PackageCombinedClientTask>("packageCombinedClient") {
    description = "Packages Stage C: the complete verified Distant Horizons and Angelica client overlay."
    dependsOn(verifyRequiredRuntimeArtifacts)
    configureCombinedInputs(
        "combined-client",
        "DistantHorizons-WDG-Combined-Client",
        includeDh = true,
        includeShaders = true,
    )
    outputFile.set(layout.buildDirectory.file("combined-client/packages/DistantHorizons-WDG-combined-client.zip"))
    reportFile.set(layout.buildDirectory.file("verification/combined-client-package.properties"))
}

val verifyCombinedClientPackage =
    tasks.register<VerifyCombinedClientPackageTask>("verifyCombinedClientPackage") {
        description = "Verifies the deterministic Stage C full combined-client overlay."
        configureCombinedVerification(
            packageCombinedClient,
            "combined-client",
            "DistantHorizons-WDG-Combined-Client",
            includeDh = true,
            includeShaders = true,
        )
        reportFile.set(layout.buildDirectory.file("verification/combined-client-verification.properties"))
    }

tasks.register<VerifyCombinedClientReproducibilityTask>("verifyCombinedClientReproducibility") {
    group = "verification"
    description = "Creates the Stage C package twice in isolated outputs and compares exact SHA-256 values."
    dependsOn(verifyRequiredRuntimeArtifacts)
    distantHorizonsVersion.set(unifiedModVersion)
    distantHorizonsJar.set(productionModArtifactForVerification)
    generatedRefmap.set(generatedMixinRefmap)
    lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
    gtnhLibJar.set(localGtnhLibArtifact)
    uniMixinsJar.set(localUniMixinsArtifact)
    angelicaJar.set(localAngelicaArtifact)
    runtimeBundle.set(localLwjgl3ifyRuntimeBundle)
    firstPackage.set(layout.buildDirectory.file("combined-client/reproducibility/combined-client-first.zip"))
    secondPackage.set(layout.buildDirectory.file("combined-client/reproducibility/combined-client-second.zip"))
    reportFile.set(layout.buildDirectory.file("verification/combined-client-reproducibility.properties"))
}

tasks.register("explainCombinedClientInputs") {
    group = "help"
    description = "Prints the explicit Change 006 external-artifact property contract."
    doLast {
        logger.lifecycle("Required properties: wdgLwjgl3ifyProductionJar, wdgLwjgl3ifyBundledClientPackage, wdgLwjgl3ifyRuntimeBundle, wdgAngelicaJar, wdgUniMixinsJar, wdgGtnhLibJar")
        logger.lifecycle("Optional source-helper property: wdgGtnhLibSourceZip (present={})", localGtnhLibSourceZip.isPresent)
    }
}

tasks.register("explainProductionLaunchContract") {
    group = "help"
    description = "Explains the deliberately deferred production-like client launch boundary."
    doLast {
        logger.lifecycle("DistantHorizons-WDG Change 006 packages isolated production-client overlays but does not launch Minecraft from Gradle.")
        logger.lifecycle("Production DH artifact provider: reobfJar -> {}", productionModArtifact.get().asFile)
        logger.lifecycle("Required game JVM: Java 21 (class-file major {}).", FoundationSupport.JAVA_21_CLASS_MAJOR)
        logger.lifecycle("Change 006 outputs: Stage A bootstrap, Stage B required Distant Horizons stack, and Stage C full combined client.")
    }
}

plugins.withType<com.diffplug.gradle.spotless.SpotlessPlugin> {
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            targetExclude(
                "src/*/java/com/seibel/distanthorizons/core/**",
                "src/*/java/com/seibel/distanthorizons/api/**",
                "src/*/java/com/seibel/distanthorizons/coreapi/**",
                "src/*/java/com/seibel/distanthorizons/common/render/**",
            )
        }
    }
}

tasks.withType<org.gradle.api.plugins.quality.Checkstyle>().configureEach {
    exclude(
        "**/com/seibel/distanthorizons/core/**",
        "**/com/seibel/distanthorizons/api/**",
        "**/com/seibel/distanthorizons/coreapi/**",
        "**/com/seibel/distanthorizons/common/render/**",
    )
}
