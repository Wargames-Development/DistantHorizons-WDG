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

val localLwjgl3ifyProductionPath = providers.gradleProperty("wdgLwjgl3ifyProductionJar")
val localLwjgl3ifyProductionArtifact = layout.file(localLwjgl3ifyProductionPath.map(::File))

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

tasks.register("explainProductionLaunchContract") {
    group = "help"
    description = "Explains the deliberately deferred production-like client launch boundary."
    doLast {
        logger.lifecycle("DistantHorizons-WDG Change 005 does not launch Minecraft.")
        logger.lifecycle("Production DH artifact provider: reobfJar -> {}", productionModArtifact.get().asFile)
        logger.lifecycle("Required game JVM: Java 21 (class-file major {}).", FoundationSupport.JAVA_21_CLASS_MAJOR)
        logger.lifecycle("Next bounded change: isolated client with exact DH reobf JAR, exact lwjgl3ify-wdg reobf JAR, required runtime mods, and packaged Java 21 bundle.")
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
