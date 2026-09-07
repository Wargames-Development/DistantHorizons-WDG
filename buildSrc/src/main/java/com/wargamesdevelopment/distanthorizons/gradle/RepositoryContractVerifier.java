package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public final class RepositoryContractVerifier {

    private static final List<String> EXPECTED_MIGRATIONS = List.of(
        "0010-sqlite-createInitialDataTables.sql",
        "0020-sqlite-createFullDataSourceV2Tables.sql",
        "0030-sqlite-changeTableJournaling.sql",
        "0031-sqlite-useSqliteWalJournaling.sql",
        "0040-sqlite-removeRenderCache.sql",
        "0050-sqlite-addApplyToParentIndex.sql",
        "0060-sqlite-createChunkHashTable.sql",
        "0070-sqlite-createBeaconBeamTable.sql",
        "0080-sqlite-addApplyToChildrenColumn.sql",
        "0090-sqlite-addAdjacentFullDataColumns.sql",
        "0100-sqlite-deleteLowDetailDataForRegen.sql"
    );

    private static final List<String> REQUIRED_FILES = List.of(
        "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "build.gradle.kts",
        "gradle.properties", "dependencies.gradle", ".gitignore", "README.md", "SETUP.md", "COMPILING.md",
        "docs/COMBINED_CLIENT.md", "docs/DEPENDENCIES.md", "docs/RELEASE_CANDIDATE.md",
        "docs/INSTALLATION.md", "docs/UPGRADE.md", "docs/KNOWN_CONFLICTS.md", "docs/PERFORMANCE.md",
        "docs/ROLLBACK.md", "docs/WINDOWS_TESTING.md", "docs/SERVER_COMPATIBILITY.md",
        "docs/PERFORMANCE_ACCEPTANCE_TEMPLATE.md", "docs/VALIDATION_RESULTS_TEMPLATE.md",
        "scripts/package-source.sh", "scripts/build-gtnhlib-0.11.31.sh", "scripts/validate-change007.sh",
        "scripts/collect-macos-rc-evidence.sh", "scripts/Collect-Windows-RcEvidence.ps1",
        "scripts/finalize-change007-release.sh", "scripts/cleanup-change007.sh",
        "src/main/resources/mcmod.info", "src/main/resources/sqlScripts/scriptList.txt",
        "src/main/java/com/seibel/distanthorizons/coreapi/ModInfo.java",
        "src/main/java/com/seibel/distanthorizons/coreapi/ReleaseChannel.java",
        "src/main/java/com/seibel/distanthorizons/coreapi/WdgVersionPolicy.java",
        "src/main/java/com/seibel/distanthorizons/coreapi/BuildWarningMessages.java",
        "src/main/java/com/seibel/distanthorizons/coreapi/SingleLineChatMessages.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/BuildInfo.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/BuildInfoParser.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/BuildInfoResourceLoader.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/ModJarInfo.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/UpdaterPolicy.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/updater/UpdaterPolicyManager.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/updater/UpdaterPolicyDecision.java",
        "src/main/java/com/seibel/distanthorizons/core/jar/updater/UpdaterExecutionGate.java",
        "src/main/java/com/seibel/distanthorizons/core/config/WdgFreshProfileDefaults.java",
        "src/main/java/com/seibel/distanthorizons/core/config/file/ConfigVersionPolicy.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/ProvenanceSupport.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/GenerateBuildInfoTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/BuildInfoArtifactVerifier.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/ReleaseCandidateSupport.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/ReleaseCandidateInputsTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/PackageWdgReleaseCandidateTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/PackageCurseForgeTestingProfileTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyWdgReleaseCandidateReproducibilityTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyWdgReleaseCandidateTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/ModpackAuditSupport.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/AuditDedicatedServerTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/AuditWargamesModpackCompatibilityTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/StableReleaseGate.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyStableReleaseGateTask.java",
        "src/test/java/com/seibel/distanthorizons/coreapi/BuildWarningMessagesTest.java",
        "src/test/java/com/seibel/distanthorizons/coreapi/SingleLineChatMessagesTest.java",
        "src/test/java/com/seibel/distanthorizons/coreapi/ReleaseChannelTest.java",
        "src/test/java/com/seibel/distanthorizons/coreapi/WdgVersionPolicyTest.java",
        "src/test/java/com/seibel/distanthorizons/core/jar/BuildInfoParserTest.java",
        "src/test/java/com/seibel/distanthorizons/core/jar/BuildInfoResourceLoaderTest.java",
        "src/test/java/com/seibel/distanthorizons/core/jar/updater/UpdaterPolicyDecisionTest.java",
        "src/test/java/com/seibel/distanthorizons/core/jar/updater/UpdaterExecutionGateTest.java",
        "src/test/java/com/seibel/distanthorizons/core/config/WdgFreshProfileDefaultsTest.java"
    );

    private static final List<String> FORBIDDEN_TRACKED_PREFIXES = List.of(
        ".gradle/", "build/", "buildSrc/build/", "buildSrc/.gradle/", "run/", "eclipse/", ".idea/",
        ".vscode/", "logs/", "crash-reports/", "config/", "saves/", "combined-client/",
        "validation-logs/", "native/", "natives/", "curseforge-profiles/", "external-build/",
        "release-candidates/", "performance-reports/", "modpack-audit-inputs/", "server-audit-inputs/"
    );

    private RepositoryContractVerifier() {}

    public static Map<String, Object> verify(Path projectDir, String expectedVersion) throws IOException {
        projectDir = projectDir.toAbsolutePath().normalize();
        expectedVersion = FoundationSupport.validateVersion(expectedVersion);
        if (!ReleaseCandidateSupport.VERSION.equals(expectedVersion)) {
            throw new IllegalStateException("Change 007 version mismatch: " + expectedVersion);
        }
        for (String file : REQUIRED_FILES) requireFile(projectDir, file);

        Properties gradle = FoundationSupport.loadProperties(projectDir.resolve("gradle.properties"));
        requireProperty(gradle, "modId", FoundationSupport.MOD_ID);
        requireProperty(gradle, "modVersion", ReleaseCandidateSupport.VERSION);
        requireProperty(gradle, "minecraftVersion", "1.7.10");
        requireProperty(gradle, "forgeVersion", "10.13.4.1614");
        requireProperty(gradle, "forceToolchainVersion", "21");
        requireProperty(gradle, "wdgReleaseChannel", "RELEASE_CANDIDATE");
        requireProperty(gradle, "wdgProvenanceMode", "VALIDATION");
        requireProperty(gradle, "wdgStableConfirmation", "false");

        String modInfo = read(projectDir, "src/main/java/com/seibel/distanthorizons/coreapi/ModInfo.java");
        require(modInfo, "public static final String VERSION = Tags.VERSION;", "generated version token");
        require(modInfo, "RELEASE_CHANNEL", "explicit release channel");
        require(modInfo, "IS_RELEASE_CANDIDATE", "release-candidate indicator");
        require(modInfo, "PROTOCOL_VERSION = 15", "network protocol");
        require(modInfo, "API_MAJOR_VERSION = 7", "API major");
        require(modInfo, "API_MINOR_VERSION = 0", "API minor");
        require(modInfo, "API_PATCH_VERSION = 0", "API patch");
        require(modInfo, "CONFIG_FILE_VERSION = 4", "config version");

        String mcmod = read(projectDir, "src/main/resources/mcmod.info");
        require(mcmod, "\"version\": \"${modVersion}\"", "mcmod version token");
        Set<String> requiredMods = FoundationSupport.extractJsonStringArray(mcmod, "requiredMods");
        if (!requiredMods.equals(Set.of("lwjgl3ify", "gtnhlib", "gtnhmixins"))) {
            throw new IllegalStateException("mcmod.info requiredMods changed: " + requiredMods);
        }

        String clientApi = read(projectDir, "src/main/java/com/seibel/distanthorizons/core/api/internal/ClientApi.java");
        require(clientApi, "BuildWarningMessages.forCurrentBuild()", "warning builder use");
        require(clientApi, "sendChatMessages", "separate warning line submission");
        reject(clientApi, "nightly/unstable build, version: [" + " + ModInfo.VERSION + " + "]\\n", "embedded multiline warning");
        String warning = read(projectDir, "src/main/java/com/seibel/distanthorizons/coreapi/BuildWarningMessages.java");
        for (String marker : List.of("DEVELOPMENT", "RELEASE_CANDIDATE", "STABLE", "List.of", "indexOf('\\n')", "indexOf('\\r')")) {
            require(warning, marker, "warning model");
        }

        String chatMessages = read(projectDir, "src/main/java/com/seibel/distanthorizons/coreapi/SingleLineChatMessages.java");
        require(chatMessages, "submitText(String text", "legacy multiline chat splitter");
        require(chatMessages, "split(\"\\n\", -1)", "line-feed splitting");
        String minecraftClientWrapper = read(projectDir, "src/main/java/com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftClientWrapper.java");
        require(minecraftClientWrapper, "SingleLineChatMessages.submitText(string", "central chat line-safe submission");
        reject(minecraftClientWrapper, "new ChatComponentText(string)", "unsplit multiline chat component");

        String modJarInfo = read(projectDir, "src/main/java/com/seibel/distanthorizons/core/jar/ModJarInfo.java");
        require(modJarInfo, "RESOURCE_PATH = \"/build_info.json\"", "canonical build-info path");
        require(modJarInfo, "getResourceAsStream(RESOURCE_PATH)", "canonical build-info lookup");
        require(modJarInfo, "input == null", "missing resource check");
        reject(modJarInfo, "printStackTrace", "raw build-info exception");
        require(modJarInfo, "Git_Branch = INFO.branchOrChannel", "branch assignment");
        require(modJarInfo, "Git_Commit = INFO.commit", "commit assignment");

        String updater = read(projectDir, "src/main/java/com/seibel/distanthorizons/core/jar/updater/SelfUpdater.java");
        int policy = updater.indexOf("UpdaterPolicyManager.allowsUpstreamUpdater()");
        int modrinth = updater.indexOf("ModrinthGetter.init()");
        int gitlab = updater.indexOf("GitlabGetter.INSTANCE.projectPipelines");
        if (policy < 0 || (modrinth >= 0 && policy > modrinth) || (gitlab >= 0 && policy > gitlab)) {
            throw new IllegalStateException("Managed updater gate must precede upstream services");
        }
        require(updater, "deleteOldJarOnJvmShutdown = false", "managed updater deletion suppression");

        String config = read(projectDir, "src/main/java/com/seibel/distanthorizons/core/config/Config.java");
        for (String marker : List.of(
            "WdgFreshProfileDefaults.QUALITY_PRESET", "WdgFreshProfileDefaults.THREAD_PRESET",
            "WdgFreshProfileDefaults.LOD_RENDER_DISTANCE_RADIUS", "WdgFreshProfileDefaults.GENERATOR_MODE",
            "WdgFreshProfileDefaults.AUTO_UPDATER_ENABLED", "WdgFreshProfileDefaults.SILENT_UPDATER_ENABLED"
        )) require(config, marker, "fresh-profile defaults");

        String build = read(projectDir, "build.gradle.kts");
        for (String task : List.of(
            "generateBuildInfo", "verifyBuildInfo", "verifyProductionModArtifact", "packageBootstrapSmokeClient",
            "packageDistantHorizonsSmokeClient", "packageCombinedClient", "verifyCombinedClientReproducibility",
            "packageWdgReleaseCandidate", "verifyWdgReleaseCandidate", "verifyWdgReleaseCandidateReproducibility",
            "packageCurseForgeTestingProfile", "auditWargamesModpackCompatibility", "auditDedicatedServer",
            "verifyFutureStableReleaseGate"
        )) require(build, "\"" + task + "\"", "Change 007 task wiring");
        require(build, "MANAGED_DISABLED", "JAR manifest updater policy");
        require(build, "RELEASE_CANDIDATE", "JAR manifest release channel");

        String packageSupport = read(projectDir, "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/CombinedClientSupport.java");
        require(packageSupport, "distantHorizonsCommit(inputs.distantHorizons())", "artifact-derived commit");
        require(packageSupport, "lwjgl3ifyRuntimeDistribution", "embedded-runtime package manifest");
        require(
            packageSupport,
            "runtimeDistributionMode",
            "one-JAR runtime distribution mode"
        );
        require(packageSupport, "optionalManualExtensions", "optional architecture extensions");
        reject(packageSupport, "File runtimeBundle", "active combined-client split-runtime input");
        reject(packageSupport, "inputs.runtimeBundle()", "active combined-client split-runtime use");
        reject(packageSupport, "MemberSource.file(inputs.runtimeBundle", "active combined-client runtime copy");

        for (String taskSource : List.of(
            "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/PackageCombinedClientTask.java",
            "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientPackageTask.java",
            "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientReproducibilityTask.java",
            "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRequiredRuntimeArtifactsTask.java",
            "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/ReleaseCandidateInputsTask.java",
            "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyWdgReleaseCandidateTask.java"
        )) {
            String taskText = read(projectDir, taskSource);
            reject(taskText, "getRuntimeBundle", "active typed split-runtime property in " + taskSource);
            reject(taskText, "wdgLwjgl3ifyRuntimeBundle", "obsolete runtime property in " + taskSource);
            reject(taskText, "wdgLwjgl3ifyBundledClientPackage", "obsolete overlay property in " + taskSource);
        }

        String releaseSupport = read(projectDir, "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/ReleaseCandidateSupport.java");
        require(releaseSupport, "RELEASE_MEMBER_COUNT = 17", "one-JAR release member count");
        require(releaseSupport, "CURSEFORGE_MEMBER_COUNT = 9", "one-JAR CurseForge member count");
        require(releaseSupport, "rejectObsoleteExternalRuntimeMembers", "obsolete runtime package rejection");
        reject(releaseSupport, "File runtimeBundle", "release-candidate split-runtime input");
        reject(releaseSupport, "inputs.runtimeBundle()", "release-candidate split-runtime use");
        reject(releaseSupport, "members.put(root + \"lwjgl3ify/runtime/", "release-candidate runtime directory");
        reject(releaseSupport, "members.put(\"overrides/lwjgl3ify/runtime/", "CurseForge runtime directory");

        String lwjglVerifier = read(projectDir, "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/Lwjgl3ifyCompatibilityVerifier.java");
        for (String marker : List.of(
            "lwjgl3ify-3.0.28-master.5+d7e60f5a0d.jar",
            "207_893_285L",
            "ef1ec515dc56fac04c6c9791d4d39b62d3b954b441070cffacc0a8516b9136ca",
            "d7e60f5a0dea4aa348e3c06b8f0a87c171522a37",
            "change-005-verified-embedded-java-runtime-distribution",
            "EMBEDDED_PRIMARY_RUNTIMES",
            "META-INF/lwjgl3ify-wdg/runtime-distribution.json",
            "linux-x86_64.tar.gz",
            "macos-aarch64.tar.gz",
            "macos-x86_64.tar.gz",
            "windows-x86_64.zip",
            "embeddedRuntimeCount",
            "optionalRuntimeExtensionPlatforms"
        )) {
            require(lwjglVerifier, marker, "runtime-bearing lwjgl3ify verifier");
        }

        for (String obsolete : List.of(
            "wdgLwjgl3ifyBundledClientPackage",
            "wdgLwjgl3ifyRuntimeBundle",
            "verifyNormalizedRuntimeBundle"
        )) {
            reject(build, obsolete, "active one-JAR Gradle contract");
        }

        for (String doc : List.of(
            "README.md", "COMPILING.md", "SETUP.md", "docs/COMBINED_CLIENT.md",
            "docs/DEPENDENCIES.md", "docs/INSTALLATION.md", "docs/RELEASE_CANDIDATE.md",
            "docs/UPGRADE.md", "docs/ROLLBACK.md", "docs/WINDOWS_TESTING.md"
        )) {
            String document = read(projectDir, doc);
            reject(document, "wdgLwjgl3ifyBundledClientPackage", "obsolete overlay property in " + doc);
            reject(document, "wdgLwjgl3ifyRuntimeBundle", "obsolete runtime property in " + doc);
            reject(document, "lwjgl3ify-wdg-java21-runtimes.zip", "obsolete external runtime ZIP in " + doc);
        }

        String finalizer = read(projectDir, "scripts/finalize-change007-release.sh");
        require(finalizer, "wdgLwjgl3ifyProductionJar", "final one-JAR input");
        reject(finalizer, "wdgLwjgl3ifyRuntimeBundle", "obsolete finalizer runtime input");
        reject(finalizer, "wdgLwjgl3ifyBundledClientPackage", "obsolete finalizer overlay input");

        List<String> migrations = Files.readAllLines(projectDir.resolve("src/main/resources/sqlScripts/scriptList.txt"), StandardCharsets.UTF_8)
            .stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (!migrations.equals(EXPECTED_MIGRATIONS)) {
            throw new IllegalStateException("SQL migration ordering changed: " + migrations);
        }
        for (String migration : migrations) requireFile(projectDir, "src/main/resources/sqlScripts/" + migration);

        String sourcePackager = read(projectDir, "scripts/package-source.sh");
        require(sourcePackager, "git -C \"$repository\" ls-files --cached --others --exclude-standard", "tracked/untracked source inventory");
        for (String marker : List.of("build/", ".gradle/", "external-build/", "release-candidates/", "curseforge-profiles/", "*.jar", "*.zip")) {
            require(read(projectDir, ".gitignore"), marker, "generated release exclusion");
        }
        for (String executable : List.of(
            "scripts/package-source.sh", "scripts/build-gtnhlib-0.11.31.sh", "scripts/validate-change007.sh",
            "scripts/collect-macos-rc-evidence.sh", "scripts/finalize-change007-release.sh", "scripts/cleanup-change007.sh"
        )) {
            if (!Files.isExecutable(projectDir.resolve(executable))) {
                throw new IllegalStateException("Required script is not executable: " + executable);
            }
        }

        validateTrackedFiles(projectDir);
        validateNoTemplatePackage(projectDir);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("version", expectedVersion);
        report.put("releaseChannel", ProvenanceSupport.RELEASE_CHANNEL);
        report.put("updaterPolicy", ProvenanceSupport.UPDATER_POLICY);
        report.put("apiVersion", "7.0.0");
        report.put("protocolVersion", 15);
        report.put("configVersion", 4);
        report.put("sqlMigrationCount", migrations.size());
        report.put("packageContract", ProvenanceSupport.PACKAGE_CONTRACT);
        report.put("lwjgl3ifyCommit", Lwjgl3ifyCompatibilityVerifier.EXPECTED_COMMIT);
        report.put("runtimeDistributionMode", Lwjgl3ifyCompatibilityVerifier.RUNTIME_DISTRIBUTION_MODE);
        report.put("embeddedPrimaryRuntimeCount", Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES.size());
        report.put("externalRuntimeBundleRequired", false);
        report.put("bundledClientOverlayRequired", false);
        report.put("verified", true);
        return report;
    }

    private static void requireFile(Path root, String relative) {
        Path file = root.resolve(relative);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalStateException("Required repository file is missing: " + relative);
        }
    }

    private static String read(Path root, String relative) throws IOException {
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }

    private static void requireProperty(Properties properties, String key, String expected) {
        String actual = properties.getProperty(key);
        actual = actual == null ? null : actual.trim();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("gradle.properties mismatch for " + key + ": " + actual + " != " + expected);
        }
    }

    private static void require(String text, String marker, String label) {
        if (!text.contains(marker)) throw new IllegalStateException(label + " missing marker: " + marker);
    }

    private static void reject(String text, String marker, String label) {
        if (text.contains(marker)) throw new IllegalStateException(label + " contains forbidden marker: " + marker);
    }

    private static void validateTrackedFiles(Path projectDir) throws IOException {
        if (!Files.isDirectory(projectDir.resolve(".git"))) return;
        Process process = new ProcessBuilder("git", "-C", projectDir.toString(), "ls-files")
            .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            int rc = process.waitFor();
            if (rc != 0) throw new IllegalStateException("git ls-files failed: " + output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking tracked files", e);
        }
        List<String> forbidden = new ArrayList<>();
        for (String name : output.lines().toList()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_TRACKED_PREFIXES.stream().anyMatch(lower::startsWith)
                || lower.endsWith(".sqlite") || lower.endsWith(".sqlite3") || lower.endsWith(".db")
                || lower.endsWith(".lod") || lower.endsWith(".log") || lower.endsWith(".zip")
                || (lower.endsWith(".jar") && !name.equals("gradle/wrapper/gradle-wrapper.jar"))) {
                forbidden.add(name);
            }
        }
        if (!forbidden.isEmpty()) throw new IllegalStateException("Generated/runtime files are tracked: " + forbidden);
    }

    private static void validateNoTemplatePackage(Path projectDir) throws IOException {
        try (Stream<Path> files = Files.walk(projectDir.resolve("src"))) {
            List<Path> bad = files.filter(Files::isRegularFile)
                .filter(path -> path.toString().replace('\\', '/').contains("com/myname/mymodid")).toList();
            if (!bad.isEmpty()) throw new IllegalStateException("Template package remains: " + bad);
        }
    }
}
