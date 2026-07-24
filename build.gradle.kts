import com.wargamesdevelopment.distanthorizons.gradle.AuditDedicatedServerTask
import com.wargamesdevelopment.distanthorizons.gradle.AuditWargamesModpackCompatibilityTask
import com.wargamesdevelopment.distanthorizons.gradle.PackageCurseForgeTestingProfileTask
import com.wargamesdevelopment.distanthorizons.gradle.PackageWdgReleaseCandidateTask
import com.wargamesdevelopment.distanthorizons.gradle.ReleaseCandidateSupport
import com.wargamesdevelopment.distanthorizons.gradle.ReleaseCandidateInputsTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyStableReleaseGateTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyWdgReleaseCandidateReproducibilityTask
import com.wargamesdevelopment.distanthorizons.gradle.VerifyWdgReleaseCandidateTask
import com.wargamesdevelopment.distanthorizons.gradle.CombinedClientSupport
import com.wargamesdevelopment.distanthorizons.gradle.GenerateBuildInfoTask
import com.wargamesdevelopment.distanthorizons.gradle.ProvenanceSupport
import com.wargamesdevelopment.distanthorizons.gradle.VerifyBuildInfoTask
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
import java.nio.charset.StandardCharsets

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val unifiedModVersion = providers.gradleProperty("modVersion")
    .map(FoundationSupport::validateVersion)
    .get()
version = unifiedModVersion
extensions.extraProperties["modVersion"] = unifiedModVersion

val wdgReleaseChannel = providers.gradleProperty("wdgReleaseChannel")
    .orElse("RELEASE_CANDIDATE")
val wdgProvenanceMode = providers.gradleProperty("wdgProvenanceMode")
    .orElse("VALIDATION")
if (wdgReleaseChannel.get() != "RELEASE_CANDIDATE") {
    throw GradleException("Change 007 only permits wdgReleaseChannel=RELEASE_CANDIDATE")
}

val buildInfoSourceFiles = files(
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
            "source-packages/**",
            "distributions/**",
            "combined-client/**",
            "staging/**",
            "native/**",
            "natives/**",
            "external-build/**",
            "gtnhlib-build/**",
            "runtime-packages/**",
            "release-candidates/**",
            "curseforge-profiles/**",
            "performance-reports/**",
            "modpack-audit-inputs/**",
            "server-audit-inputs/**",
            "change007-*-evidence/**",
            "**/.DS_Store",
            "**/__MACOSX/**",
            "**/*.jar",
            "**/*.zip",
            "**/*.tar",
            "**/*.tar.gz",
            "**/*.tgz",
            "**/*.log",
            "**/*.sqlite",
            "**/*.sqlite3",
            "**/*.db",
            "**/*.db-journal",
            "**/*.db-wal",
            "**/*.db-shm",
            "**/*.lod",
            "**/*.jre",
            "**/*.jdk",
        )
    },
    file("gradle/wrapper/gradle-wrapper.jar"),
)

val generateBuildInfo = tasks.register<GenerateBuildInfoTask>("generateBuildInfo") {
    group = "build"
    description = "Generates deterministic Change 007 build provenance as build_info.json."
    repositoryDirectory.set(layout.projectDirectory)
    sourceFiles.from(buildInfoSourceFiles)
    modId.set(providers.gradleProperty("modId"))
    modVersion.set(unifiedModVersion)
    minecraftVersion.set(providers.gradleProperty("minecraftVersion"))
    forgeVersion.set(providers.gradleProperty("forgeVersion"))
    provenanceMode.set(wdgProvenanceMode)
    explicitSourceCommit.set(providers.gradleProperty("wdgSourceCommit"))
    explicitSourceBranch.set(providers.gradleProperty("wdgSourceBranch"))
    explicitSourceTreeState.set(providers.gradleProperty("wdgSourceTreeState"))
    explicitSourceTreeDigest.set(providers.gradleProperty("wdgSourceTreeDigest"))
    expectedFinalCommit.set(providers.gradleProperty("wdgExpectedCommit"))
    outputFile.set(layout.buildDirectory.file("generated/wdg-build-info/build_info.json"))
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/wdg-build-info"))
}

tasks.processResources {
    dependsOn(generateBuildInfo)
    inputs.file(generateBuildInfo.flatMap { it.outputFile })
        .withPropertyName("wdgBuildInfo")
        .withPathSensitivity(PathSensitivity.NONE)
}

val generatedBuildInfoText = providers.fileContents(generateBuildInfo.flatMap { it.outputFile }).asText
val generatedSourceTreeDigest = generatedBuildInfoText.map { json ->
    Regex("\"sourceTreeDigest\"\\s*:\\s*\"([^\"]+)\"")
        .find(json)?.groupValues?.get(1)
        ?: throw GradleException("Generated build_info.json lacks sourceTreeDigest")
}
val resolvedExpectedCommit = providers.gradleProperty("wdgExpectedCommit")
    .orElse(providers.gradleProperty("wdgSourceCommit"))
    .orElse(ProvenanceSupport.BASE_COMMIT)
val releaseAssetSuffix = wdgProvenanceMode.map { mode ->
    if (mode.equals("FINAL", ignoreCase = true)) "" else ReleaseCandidateSupport.VALIDATION_SUFFIX
}

val verifyBuildInfo = tasks.register<VerifyBuildInfoTask>("verifyBuildInfo") {
    group = "verification"
    description = "Verifies the deterministic Change 007 provenance resource."
    dependsOn(generateBuildInfo)
    buildInfoFile.set(generateBuildInfo.flatMap { it.outputFile })
    expectedVersion.set(unifiedModVersion)
    expectedMode.set(wdgProvenanceMode)
    reportFile.set(layout.buildDirectory.file("verification/build-info.properties"))
}

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

    // Ordinary development run tasks remain disabled. Change 007 will establish an isolated,
    // production-like launch using exact reobfuscated DH and lwjgl3ify-wdg artifacts plus Java 21.
}

for (jarTask in listOf(tasks.jar, tasks.shadowJar, tasks.sourcesJar)) {
    jarTask.configure {
        dependsOn(generateBuildInfo)
        inputs.file(generateBuildInfo.flatMap { it.outputFile })
            .withPropertyName("wdgBuildInfoManifest")
            .withPathSensitivity(PathSensitivity.NONE)
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        manifest {
            attributes(
                "Lwjgl3ify-Aware" to true,
                "Implementation-Title" to "Distant Horizons",
                "Implementation-Version" to unifiedModVersion,
                "Implementation-Vendor" to "Wargames Development Group",
                "Distant-Horizons-Release-Channel" to "RELEASE_CANDIDATE",
                "Distant-Horizons-Updater-Policy" to "MANAGED_DISABLED",
            )
        }
        doFirst {
            val json = generateBuildInfo.get().outputFile.get().asFile.readText(StandardCharsets.UTF_8)
            fun field(name: String): String = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"")
                .find(json)?.groupValues?.get(1)
                ?: throw GradleException("Generated build_info.json lacks $name")
            manifest.attributes(
                "Distant-Horizons-Git-Commit" to field("commit"),
                "Distant-Horizons-Tree-State" to field("treeState"),
                "Distant-Horizons-Build-Source" to field("buildSource"),
                "Distant-Horizons-Source-Digest" to field("sourceTreeDigest"),
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
    dependsOn(verifyRepository, verifyBuildInfo)
}

val verifyProductionModArtifact =
    tasks.register<VerifyProductionModArtifactTask>("verifyProductionModArtifact") {
        group = "verification"
        description = "Verifies the exact reobfuscated, shadowed Distant Horizons production JAR."
        dependsOn(tasks.reobfJar, tasks.compileJava)
        artifactFile.set(productionModArtifactForVerification)
        generatedRefmap.set(generatedMixinRefmap)
        expectedVersion.set(unifiedModVersion)
        expectedProvenanceMode.set(wdgProvenanceMode)
        expectedCommit.set(resolvedExpectedCommit)
        reportFile.set(layout.buildDirectory.file("verification/production-mod-artifact.properties"))
    }

fun externalArtifact(name: String) = layout.file(
    providers.gradleProperty(name).map { configuredPath ->
        val configured = File(configuredPath)
        if (configured.isAbsolute) configured else layout.projectDirectory.file(configuredPath).asFile
    },
)

val localLwjgl3ifyProductionArtifact = externalArtifact("wdgLwjgl3ifyProductionJar")
val localAngelicaArtifact = externalArtifact("wdgAngelicaJar")
val localUniMixinsArtifact = externalArtifact("wdgUniMixinsJar")
val localGtnhLibArtifact = externalArtifact("wdgGtnhLibJar")
val localGtnhLibSourceZip = providers.gradleProperty("wdgGtnhLibSourceZip")

tasks.register<VerifyWdgLwjgl3ifyCompatibilityTask>("verifyWdgLwjgl3ifyCompatibility") {
    group = "verification"
    description = "Checks an explicitly supplied lwjgl3ify-wdg production reobf JAR against the Change 005 embedded-runtime contract."
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

val verifyRequiredRuntimeArtifacts =
    tasks.register<VerifyRequiredRuntimeArtifactsTask>("verifyRequiredRuntimeArtifacts") {
        group = "verification"
        description = "Deeply verifies every exact external and project artifact used by Change 007."
        dependsOn(
            verifyProductionModArtifact,
            verifyGtnhLibArtifact,
            verifyAngelicaArtifact,
            verifyUniMixinsArtifact,
            "verifyWdgLwjgl3ifyCompatibility",
        )
        distantHorizonsJar.set(productionModArtifactForVerification)
        generatedRefmap.set(generatedMixinRefmap)
        distantHorizonsVersion.set(unifiedModVersion)
        provenanceMode.set(wdgProvenanceMode)
        expectedCommit.set(resolvedExpectedCommit)
        lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
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
    provenanceMode.set(wdgProvenanceMode)
    expectedCommit.set(resolvedExpectedCommit)
    sourceTreeDigest.set(generatedSourceTreeDigest)
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
    provenanceMode.set(wdgProvenanceMode)
    expectedCommit.set(resolvedExpectedCommit)
    sourceTreeDigest.set(generatedSourceTreeDigest)
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
}

val packageBootstrapSmokeClient = tasks.register<PackageCombinedClientTask>("packageBootstrapSmokeClient") {
    description = "Packages Stage A: the runtime-bearing lwjgl3ify Change 005 JAR, GTNHLib, and UniMixins/GTNHMixins."
    dependsOn(
        verifyGtnhLibArtifact,
        verifyUniMixinsArtifact,
        "verifyWdgLwjgl3ifyCompatibility",
    )
    configureCombinedInputs(
        "bootstrap-smoke",
        "DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-A",
        includeDh = false,
        includeShaders = false,
    )
    outputFile.set(layout.buildDirectory.file(releaseAssetSuffix.map { suffix ->
        "combined-client/packages/DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-A${suffix}.zip"
    }))
    reportFile.set(layout.buildDirectory.file("verification/bootstrap-smoke-package.properties"))
}

val verifyBootstrapSmokeClient =
    tasks.register<VerifyCombinedClientPackageTask>("verifyBootstrapSmokeClient") {
        description = "Verifies Stage A uses the embedded-runtime lwjgl3ify one-JAR contract without an external runtime ZIP."
        configureCombinedVerification(
            packageBootstrapSmokeClient,
            "bootstrap-smoke",
            "DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-A",
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
            "verifyWdgLwjgl3ifyCompatibility",
        )
        configureCombinedInputs(
            "distant-horizons-required-smoke",
            "DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-B",
            includeDh = true,
            includeShaders = false,
        )
        outputFile.set(layout.buildDirectory.file(releaseAssetSuffix.map { suffix ->
            "combined-client/packages/DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-B${suffix}.zip"
        }))
        reportFile.set(layout.buildDirectory.file("verification/dh-required-smoke-package.properties"))
    }

val verifyDistantHorizonsSmokeClient =
    tasks.register<VerifyCombinedClientPackageTask>("verifyDistantHorizonsSmokeClient") {
        description = "Verifies the deterministic Stage B Distant Horizons required-stack overlay."
        configureCombinedVerification(
            packageDistantHorizonsSmokeClient,
            "distant-horizons-required-smoke",
            "DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-B",
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
        "DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-C",
        includeDh = true,
        includeShaders = true,
    )
    outputFile.set(layout.buildDirectory.file(releaseAssetSuffix.map { suffix ->
        "combined-client/packages/DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-C${suffix}.zip"
    }))
    reportFile.set(layout.buildDirectory.file("verification/combined-client-package.properties"))
}

val verifyCombinedClientPackage =
    tasks.register<VerifyCombinedClientPackageTask>("verifyCombinedClientPackage") {
        description = "Verifies the deterministic Stage C full combined-client overlay."
        configureCombinedVerification(
            packageCombinedClient,
            "combined-client",
            "DistantHorizons-WDG-3.0.4-b-wdg-rc.1-Stage-C",
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
    provenanceMode.set(wdgProvenanceMode)
    expectedCommit.set(resolvedExpectedCommit)
    sourceTreeDigest.set(generatedSourceTreeDigest)
    distantHorizonsJar.set(productionModArtifactForVerification)
    generatedRefmap.set(generatedMixinRefmap)
    lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
    gtnhLibJar.set(localGtnhLibArtifact)
    uniMixinsJar.set(localUniMixinsArtifact)
    angelicaJar.set(localAngelicaArtifact)
    firstPackage.set(layout.buildDirectory.file("combined-client/reproducibility/combined-client-first.zip"))
    secondPackage.set(layout.buildDirectory.file("combined-client/reproducibility/combined-client-second.zip"))
    reportFile.set(layout.buildDirectory.file("verification/combined-client-reproducibility.properties"))
}


fun ReleaseCandidateInputsTask.configureReleaseCandidateInputs() {
    dependsOn(verifyRequiredRuntimeArtifacts)
    distantHorizonsJar.set(productionModArtifactForVerification)
    generatedRefmap.set(generatedMixinRefmap)
    lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
    gtnhLibJar.set(localGtnhLibArtifact)
    uniMixinsJar.set(localUniMixinsArtifact)
    angelicaJar.set(localAngelicaArtifact)
    documentationDirectory.set(layout.projectDirectory.dir("docs"))
    provenanceMode.set(wdgProvenanceMode)
    expectedCommit.set(resolvedExpectedCommit)
    sourceTreeDigest.set(generatedSourceTreeDigest)
}

val packageWdgReleaseCandidate = tasks.register<PackageWdgReleaseCandidateTask>("packageWdgReleaseCandidate") {
    group = "distribution"
    description = "Creates the deterministic Change 007 WDG release-candidate root archive."
    configureReleaseCandidateInputs()
    outputFile.set(layout.buildDirectory.file(releaseAssetSuffix.map { suffix ->
        "release-candidates/${ReleaseCandidateSupport.ROOT}${suffix}.zip"
    }))
    reportFile.set(layout.buildDirectory.file("verification/wdg-release-candidate-package.properties"))
}

val verifyWdgReleaseCandidate = tasks.register<VerifyWdgReleaseCandidateTask>("verifyWdgReleaseCandidate") {
    group = "verification"
    description = "Independently verifies exact release-candidate contents, provenance, documents and checksums."
    dependsOn(packageWdgReleaseCandidate)
    releaseArchive.set(packageWdgReleaseCandidate.flatMap { it.outputFile })
    distantHorizonsJar.set(productionModArtifactForVerification)
    generatedRefmap.set(generatedMixinRefmap)
    lwjgl3ifyJar.set(localLwjgl3ifyProductionArtifact)
    gtnhLibJar.set(localGtnhLibArtifact)
    uniMixinsJar.set(localUniMixinsArtifact)
    angelicaJar.set(localAngelicaArtifact)
    documentationDirectory.set(layout.projectDirectory.dir("docs"))
    provenanceMode.set(wdgProvenanceMode)
    expectedCommit.set(resolvedExpectedCommit)
    sourceTreeDigest.set(generatedSourceTreeDigest)
    reportFile.set(layout.buildDirectory.file("verification/wdg-release-candidate-verification.properties"))
}

tasks.register<VerifyWdgReleaseCandidateReproducibilityTask>("verifyWdgReleaseCandidateReproducibility") {
    group = "verification"
    description = "Creates the full release candidate twice and compares exact bytes."
    configureReleaseCandidateInputs()
    outputFile.set(layout.buildDirectory.file("release-candidates/reproducibility/release-first.zip"))
    secondOutputFile.set(layout.buildDirectory.file("release-candidates/reproducibility/release-second.zip"))
    reportFile.set(layout.buildDirectory.file("verification/wdg-release-candidate-reproducibility.properties"))
}

tasks.register<PackageCurseForgeTestingProfileTask>("packageCurseForgeTestingProfile") {
    group = "distribution"
    description = "Creates a structurally verified private-JAR CurseForge testing profile; actual disposable import remains mandatory."
    configureReleaseCandidateInputs()
    outputFile.set(layout.buildDirectory.file(releaseAssetSuffix.map { suffix ->
        "curseforge-profiles/DistantHorizons-WDG-${ReleaseCandidateSupport.VERSION}-CurseForge-Testing${suffix}.zip"
    }))
    reportFile.set(layout.buildDirectory.file("verification/curseforge-testing-profile.properties"))
}

val modpackAuditInput = providers.gradleProperty("wdgModpackAuditInput")
tasks.register<AuditWargamesModpackCompatibilityTask>("auditWargamesModpackCompatibility") {
    group = "verification"
    description = "Audits a mods directory, export ZIP, sanitized profile ZIP, or inventory report without altering it."
    auditInput.from(modpackAuditInput.map(::File))
    jsonReport.set(layout.buildDirectory.file("verification/wargames-modpack-audit.json"))
    textReport.set(layout.buildDirectory.file("verification/wargames-modpack-audit.txt"))
}

val serverAuditInput = providers.gradleProperty("wdgServerAuditInput")
tasks.register<AuditDedicatedServerTask>("auditDedicatedServer") {
    group = "verification"
    description = "Rejects Distant Horizons client-only release artifacts in a dedicated-server input."
    serverInput.from(serverAuditInput.map(::File))
    reportFile.set(layout.buildDirectory.file("verification/dedicated-server-audit.txt"))
}


// Deliberate future-only stable gate. Change 007 remains hard-locked to RELEASE_CANDIDATE,
// so this task is documentation and enforcement infrastructure rather than a stable build path.
tasks.register<VerifyStableReleaseGateTask>("verifyFutureStableReleaseGate") {
    group = "verification"
    description = "Verifies all deliberate clean-tag/commit/acceptance conditions for a future WDG stable release."
    releaseChannel.set(providers.gradleProperty("wdgFutureStableChannel").orElse("STABLE"))
    stableConfirmation.set(providers.gradleProperty("wdgStableConfirmation").map(String::toBoolean).orElse(false))
    treeState.set(providers.gradleProperty("wdgSourceTreeState"))
    expectedTag.set(providers.gradleProperty("wdgExpectedStableTag"))
    actualTag.set(providers.gradleProperty("wdgActualStableTag"))
    expectedCommit.set(providers.gradleProperty("wdgExpectedCommit"))
    actualCommit.set(providers.gradleProperty("wdgSourceCommit"))
    acceptanceManifest.set(layout.file(providers.gradleProperty("wdgStableAcceptanceManifest").map(::File)))
    reportFile.set(layout.buildDirectory.file("verification/future-stable-release-gate.properties"))
}

tasks.register("explainCombinedClientInputs") {
    group = "help"
    description = "Prints the explicit Change 007 external-artifact property contract."
    doLast {
        logger.lifecycle("Required properties: wdgLwjgl3ifyProductionJar, wdgAngelicaJar, wdgUniMixinsJar, wdgGtnhLibJar")
        logger.lifecycle("The exact lwjgl3ify Change 005 JAR embeds the four primary Java 21 runtimes; no bundled-client overlay or external runtime ZIP property is required.")
        logger.lifecycle("Linux AArch64 and Windows AArch64 runtime extensions are optional manual assets and are not package inputs.")
        logger.lifecycle("Optional source-helper property: wdgGtnhLibSourceZip (present={})", localGtnhLibSourceZip.isPresent)
    }
}

tasks.register("explainProductionLaunchContract") {
    group = "help"
    description = "Explains the deliberately deferred production-like client launch boundary."
    doLast {
        logger.lifecycle("DistantHorizons-WDG Change 007 packages isolated production-client overlays but does not launch Minecraft from Gradle.")
        logger.lifecycle("Production DH artifact provider: reobfJar -> {}", productionModArtifact.get().asFile)
        logger.lifecycle("Required game JVM: Java 21 (class-file major {}).", FoundationSupport.JAVA_21_CLASS_MAJOR)
        logger.lifecycle("Change 007 outputs: Stage A bootstrap, Stage B required Distant Horizons stack, and Stage C full combined client.")
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
