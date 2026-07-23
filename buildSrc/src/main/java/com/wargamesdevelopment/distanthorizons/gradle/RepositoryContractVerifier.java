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

    private static final List<String> REQUIRED_FILES = List.of(
        "gradlew",
        "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
        "gradle/gradle-daemon-jvm.properties",
        "buildSrc/gradle/gradle-daemon-jvm.properties",
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        "dependencies.gradle",
        "repositories.gradle",
        "jitpack.yml",
        "README.md",
        "SETUP.md",
        "COMPILING.md",
        "docs/DEPENDENCIES.md",
        "docs/COMBINED_CLIENT.md",
        "scripts/package-source.sh",
        "scripts/build-gtnhlib-0.11.31.sh",
        "src/main/resources/mcmod.info",
        "src/main/resources/" + FoundationSupport.ACCESS_TRANSFORMER,
        "src/main/resources/" + FoundationSupport.NORMAL_MIXIN_CONFIG,
        "src/main/resources/" + FoundationSupport.EARLY_MIXIN_CONFIG,
        "src/main/resources/sqlScripts/scriptList.txt",
        "src/main/java/com/seibel/distanthorizons/DistantHorizonsTweaker.java",
        "src/main/java/com/seibel/distanthorizons/forge/ForgeMain.java",
        "src/main/java/com/seibel/distanthorizons/coreapi/ModInfo.java",
        "src/main/java/com/seibel/distanthorizons/api/DhApi.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRepositoryTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyProductionModArtifactTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyWdgLwjgl3ifyCompatibilityTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyPublishedDependencyMetadataTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/RuntimeArtifactVerifier.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/CombinedClientSupport.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRuntimeArtifactTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRequiredRuntimeArtifactsTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/PackageCombinedClientTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientPackageTask.java",
        "buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientReproducibilityTask.java",
        "buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/FoundationSupportTest.java",
        "buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/ArtifactVerifierTest.java",
        "buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/RuntimeArtifactVerifierTest.java",
        "buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/CombinedClientSupportTest.java"
    );

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

    private static final List<String> FORBIDDEN_TRACKED_PREFIXES = List.of(
        ".gradle/", "build/", "buildSrc/build/", "buildSrc/.gradle/", "run/", "eclipse/", ".idea/",
        ".vscode/", "logs/", "crash-reports/", "config/", "saves/", "combined-client/",
        "validation-logs/", "native/", "natives/", "curseforge-profiles/", "external-build/"
    );

    private RepositoryContractVerifier() {}

    public static Map<String, Object> verify(Path projectDir, String expectedVersion) throws IOException {
        projectDir = projectDir.toAbsolutePath().normalize();
        expectedVersion = FoundationSupport.validateVersion(expectedVersion);
        for (String relative : REQUIRED_FILES) {
            requireFile(projectDir, relative);
        }

        Properties wrapper = FoundationSupport.loadProperties(projectDir.resolve("gradle/wrapper/gradle-wrapper.properties"));
        String distributionUrl = wrapper.getProperty("distributionUrl", "");
        if (!distributionUrl.contains("gradle-9.4.0-bin.zip")) {
            throw new IllegalStateException("Gradle wrapper must remain 9.4.0: " + distributionUrl);
        }
        Properties daemon = FoundationSupport.loadProperties(projectDir.resolve("gradle/gradle-daemon-jvm.properties"));
        if (!"25".equals(daemon.getProperty("toolchainVersion"))) {
            throw new IllegalStateException("Gradle daemon criteria must remain Java 25");
        }
        Properties buildSrcDaemon = FoundationSupport.loadProperties(
            projectDir.resolve("buildSrc/gradle/gradle-daemon-jvm.properties")
        );
        if (!"25".equals(buildSrcDaemon.getProperty("toolchainVersion"))) {
            throw new IllegalStateException("Standalone buildSrc daemon criteria must remain Java 25");
        }

        Properties gradle = FoundationSupport.loadProperties(projectDir.resolve("gradle.properties"));
        requireProperty(gradle, "modId", FoundationSupport.MOD_ID);
        requireProperty(gradle, "modGroup", "com.seibel.distanthorizons");
        requireProperty(gradle, "modVersion", expectedVersion);
        requireProperty(gradle, "minecraftVersion", "1.7.10");
        requireProperty(gradle, "forgeVersion", "10.13.4.1614");
        requireProperty(gradle, "enableModernJavaSyntax", "modern");
        requireProperty(gradle, "forceToolchainVersion", "21");
        requireProperty(gradle, "org.gradle.configuration-cache", "false");
        requireProperty(gradle, "replaceGradleTokenInFile", "");
        requireProperty(gradle, "generateGradleTokenClass", "com.seibel.distanthorizons.coreapi.Tags");
        requireProperty(gradle, "modrinthRelations", "");
        requireProperty(gradle, "curseForgeRelations", "");
        String gradlePropertiesText = read(projectDir, "gradle.properties");
        FoundationSupport.rejectTemplateMarkers(gradlePropertiesText, "gradle.properties");

        String modInfo = read(projectDir, "src/main/java/com/seibel/distanthorizons/coreapi/ModInfo.java");
        requireContains(modInfo, "public static final String ID = \"distanthorizons\";", "ModInfo mod ID");
        requireContains(modInfo, "public static final String VERSION = Tags.VERSION;", "ModInfo generated version source");
        requireContains(modInfo, "PROTOCOL_VERSION = 15", "network protocol version");
        requireContains(modInfo, "API_MAJOR_VERSION = 7", "API major version");
        requireContains(modInfo, "API_MINOR_VERSION = 0", "API minor version");
        requireContains(modInfo, "API_PATCH_VERSION = 0", "API patch version");
        requireContains(modInfo, "CONFIG_FILE_VERSION = 4", "config file version");

        String mcmod = read(projectDir, "src/main/resources/mcmod.info");
        FoundationSupport.rejectTemplateMarkers(mcmod, "Repository mcmod.info");
        requireContains(mcmod, "\"modid\": \"${modId}\"", "mcmod.info mod ID token");
        requireContains(mcmod, "\"version\": \"${modVersion}\"", "mcmod.info version token");
        Set<String> requiredMods = FoundationSupport.extractJsonStringArray(mcmod, "requiredMods");
        Set<String> expectedRequiredMods = Set.of("lwjgl3ify", "gtnhlib", "gtnhmixins");
        if (!requiredMods.equals(expectedRequiredMods)) {
            throw new IllegalStateException("mcmod.info requiredMods mismatch: " + requiredMods);
        }

        String forgeMain = read(projectDir, "src/main/java/com/seibel/distanthorizons/forge/ForgeMain.java");
        requireContains(forgeMain, "modid = ModInfo.ID", "Forge @Mod identity");
        requireContains(forgeMain, "version = ModInfo.VERSION", "Forge @Mod version");
        requireContains(forgeMain, "required-after:lwjgl3ify", "required lwjgl3ify relationship");
        requireContains(forgeMain, "required-after:gtnhlib", "required GTNHLib relationship");
        requireContains(forgeMain, "required-after:gtnhmixins", "required UniMixins relationship");
        requireContains(forgeMain, "after:angelica", "optional Angelica ordering");
        requireContains(forgeMain, "MINIMUM_ANGELICA_VERSION = \"2.1.54\"", "Angelica compatibility floor");

        String dependencies = read(projectDir, "dependencies.gradle");
        requireContains(dependencies, "def defaultLwjgl3ify = \"com.github.GTNewHorizons:lwjgl3ify:3.0.28:dev\"", "default lwjgl3ify dependency");
        requireContains(dependencies, "def gtnhLib = \"com.github.GTNewHorizons:GTNHLib:0.11.31:dev\"", "GTNHLib dependency");
        requireContains(dependencies, "compileOnly(hodgepodge)", "optional Hodgepodge classification");
        requireContains(dependencies, "def angelica = \"com.github.GTNewHorizons:Angelica:2.1.54:dev\"", "Angelica 2.1.54 dependency");
        requireContains(dependencies, "compileOnly(angelica)", "optional Angelica classification");
        requireContains(dependencies, "wdgAngelicaJar", "exact local Angelica override");
        requireContains(dependencies, "compileOnly(\"com.github.GTNewHorizons:GT5-Unofficial:", "optional GregTech classification");
        requireContains(dependencies, "compileOnly(\"com.falsepattern:rple-mc1.7.10:", "optional RPLE classification");
        requireContains(dependencies, "wdgLwjgl3ifyDevJar", "local lwjgl3ify development override");
        requireContains(dependencies, "wdgEnableHodgepodgeRuntime", "opt-in Hodgepodge runtime");
        requireContains(dependencies, "wdgEnableNeiRuntime", "opt-in NEI runtime");
        rejectMandatoryOptionalDependency(dependencies, "api(\"com.github.GTNewHorizons:Hodgepodge");
        rejectMandatoryOptionalDependency(dependencies, "implementation(\"com.github.GTNewHorizons:Hodgepodge");
        rejectMandatoryOptionalDependency(dependencies, "runtimeOnly(\"com.github.GTNewHorizons:NotEnoughItems");

        String build = read(projectDir, "build.gradle.kts");
        for (String required : List.of(
            "verifyRepository", "verifyProductionModArtifact", "verifyWdgLwjgl3ifyCompatibility",
            "verifyPublishedDependencyMetadata", "productionModArtifact", "explainProductionLaunchContract"
        )) {
            requireContains(build, required, "build contract " + required);
        }
        requireContains(build, "JavaLanguageVersion.of(21)", "Java 21 toolchain");
        requireContains(build, "val productionModArtifact = tasks.reobfJar.flatMap { it.archiveFile }", "exact production artifact provider");
        requireContains(
            build,
            "val productionModArtifactForVerification =",
            "detached production artifact verification input");
        requireContains(
            build,
            "artifactFile.set(productionModArtifactForVerification)",
            "production verifier detached input");
        requireNotContains(
            build,
            "dependsOn(tasks.reobfJar)",
            "production verifier must not carry the incompatible RFG production graph");
        for (String disabledRun : List.of("runClient", "runServer", "runClient17", "runServer17")) {
            requireContains(build, "tasks." + disabledRun + " { enabled = false }", "controlled " + disabledRun);
        }
        for (String property : List.of(
            "wdgLwjgl3ifyProductionJar",
            "wdgLwjgl3ifyBundledClientPackage",
            "wdgLwjgl3ifyRuntimeBundle",
            "wdgAngelicaJar",
            "wdgUniMixinsJar",
            "wdgGtnhLibJar",
            "wdgGtnhLibSourceZip"
        )) {
            requireContains(build, property, "Change 006 external artifact property");
        }
        for (String task : List.of(
            "verifyGtnhLibArtifact",
            "verifyAngelicaArtifact",
            "verifyUniMixinsArtifact",
            "verifyRequiredRuntimeArtifacts",
            "packageBootstrapSmokeClient",
            "verifyBootstrapSmokeClient",
            "packageDistantHorizonsSmokeClient",
            "verifyDistantHorizonsSmokeClient",
            "packageCombinedClient",
            "verifyCombinedClientPackage",
            "verifyCombinedClientReproducibility"
        )) {
            requireContains(build, "\"" + task + "\"", "Change 006 task wiring");
        }
        requireContains(build, "tasks.reobfJar.flatMap { it.archiveFile }", "Change 006 exact Distant Horizons artifact");
        requireContains(build, "CombinedClientSupport", "Change 006 deterministic package support");

        validateMixinConfig(projectDir, FoundationSupport.NORMAL_MIXIN_CONFIG, false);
        validateMixinConfig(projectDir, FoundationSupport.EARLY_MIXIN_CONFIG, true);
        String earlyLoader = read(projectDir, "src/main/java/com/seibel/distanthorizons/DistantHorizonsTweaker.java");
        requireContains(earlyLoader, "implements IEarlyMixinLoader, IFMLLoadingPlugin", "early loading plugin contract");

        List<String> migrations = Files.readAllLines(
            projectDir.resolve("src/main/resources/sqlScripts/scriptList.txt"), StandardCharsets.UTF_8
        ).stream().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (!migrations.equals(EXPECTED_MIGRATIONS)) {
            throw new IllegalStateException("SQL migration ordering changed: " + migrations);
        }
        for (String migration : migrations) {
            requireFile(projectDir, "src/main/resources/sqlScripts/" + migration);
        }

        String readme = read(projectDir, "README.md");
        for (String marker : List.of(
            "Wargames Development Group", "DarkShadow44/DistantHorizonsStandalone", "Java 21",
            "lwjgl3ify-wdg", "Change 005", "Change 006", "docs/COMBINED_CLIENT.md",
            "Wargames-Development/DistantHorizons-WDG"
        )) {
            requireContains(readme, marker, "README contract");
        }

        String jitpack = read(projectDir, "jitpack.yml");
        requireContains(jitpack, "sdk install java 25-open", "JitPack Java 25 installation");
        requireContains(jitpack, "sdk use java 25-open", "JitPack Java 25 selection");
        requireContains(jitpack, "./gradlew --no-daemon setupCIWorkspace", "JitPack setup command");
        requireContains(jitpack, "./gradlew --no-daemon clean build publishToMavenLocal", "JitPack publication command");
        if (jitpack.contains("setupCIWorkspace//")) {
            throw new IllegalStateException("jitpack.yml still contains the broken setupCIWorkspace// command");
        }
        if (!Files.isExecutable(projectDir.resolve("scripts/package-source.sh"))) {
            throw new IllegalStateException("scripts/package-source.sh is not executable");
        }
        if (!Files.isExecutable(projectDir.resolve("scripts/build-gtnhlib-0.11.31.sh"))) {
            throw new IllegalStateException("scripts/build-gtnhlib-0.11.31.sh is not executable");
        }
        String gtnhBuildHelper = read(projectDir, "scripts/build-gtnhlib-0.11.31.sh");
        for (String marker : List.of(
            "VERSION=\"$expected_version\"",
            "reobfJar wdgPrintReobfJar",
            "expected_source_sha256",
            "NO-GIT-TAG-SET",
            "META-INF/versions/17"
        )) {
            requireContains(gtnhBuildHelper, marker, "GTNHLib source-build helper");
        }

        String gitignore = read(projectDir, ".gitignore");
        for (String marker : List.of(
            "/build/", "/.gradle/", "/run/", "*.sqlite", "*.db", "/validation-logs/",
            "/combined-client/", "/curseforge-profiles/", "/external-build/",
            "wdg-lwjgl3ify", "*.jar", "*.zip"
        )) {
            requireContains(gitignore, marker, ".gitignore hygiene");
        }

        String combinedClientDoc = read(projectDir, "docs/COMBINED_CLIENT.md");
        for (String marker : List.of(
            "VERSION=0.11.31",
            "packageBootstrapSmokeClient",
            "packageDistantHorizonsSmokeClient",
            "packageCombinedClient",
            "verifyCombinedClientReproducibility",
            "CurseForge",
            "Java 8",
            "Java 21",
            "logs/latest.log"
        )) {
            requireContains(combinedClientDoc, marker, "combined-client documentation");
        }

        String sourcePackaging = read(projectDir, "scripts/package-source.sh");
        for (String marker : List.of(
            "RuntimeArtifactVerifier.java",
            "CombinedClientSupport.java",
            "build-gtnhlib-0.11.31.sh",
            "docs/COMBINED_CLIENT.md",
            "git -C \"$repository\" ls-files --cached --others --exclude-standard"
        )) {
            requireContains(sourcePackaging, marker, "Change 006 source packaging");
        }

        validateTrackedFiles(projectDir);
        validateNoTemplatePackage(projectDir);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("projectDir", projectDir);
        report.put("modId", FoundationSupport.MOD_ID);
        report.put("rootPackage", "com.seibel.distanthorizons");
        report.put("version", expectedVersion);
        report.put("gradleWrapper", "9.4.0");
        report.put("gradleDaemonJdk", "25 ADOPTIUM");
        report.put("compileTestRuntimeJdk", "21");
        report.put("classFileMajor", FoundationSupport.JAVA_21_CLASS_MAJOR);
        report.put("apiVersion", "7.0.0");
        report.put("protocolVersion", 15);
        report.put("configVersion", 4);
        report.put("sqlMigrationCount", migrations.size());
        report.put("productionArtifactProvider", "reobfJar");
        report.put("ordinaryRunTasks", "deliberately-disabled");
        report.put("combinedClientContract", CombinedClientSupport.CONTRACT_VERSION);
        report.put("diagnosticPackages", 3);
        return report;
    }

    private static void requireFile(Path root, String relative) {
        Path path = root.resolve(relative);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("Required repository file is missing or unreadable: " + relative);
        }
    }

    private static String read(Path root, String relative) throws IOException {
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }

    private static void requireProperty(Properties properties, String key, String expected) {
        String actual = properties.getProperty(key);
        actual = actual == null ? null : actual.trim();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("gradle.properties mismatch for " + key + ": expected='" + expected + "', actual='" + actual + "'");
        }
    }

    private static void requireContains(String text, String marker, String label) {
        if (!text.contains(marker)) {
            throw new IllegalStateException(label + " is missing required marker: " + marker);
        }
    }

    private static void requireNotContains(String text, String marker, String label) {
        if (text.contains(marker)) {
            throw new IllegalStateException(label + " contains forbidden marker: " + marker);
        }
    }

    private static void rejectMandatoryOptionalDependency(String text, String marker) {
        if (text.contains(marker)) {
            throw new IllegalStateException("Optional/development integration is published as mandatory: " + marker);
        }
    }

    private static void validateMixinConfig(Path projectDir, String name, boolean requirePackage) throws IOException {
        String config = read(projectDir, "src/main/resources/" + name);
        requireContains(config, "\"refmap\": \"" + FoundationSupport.REFMAP + "\"", name + " refmap");
        if (requirePackage) {
            requireContains(config, "\"package\": \"com.seibel.distanthorizons.mixin\"", name + " package");
        }
    }

    private static void validateTrackedFiles(Path projectDir) throws IOException {
        if (!Files.isDirectory(projectDir.resolve(".git"))) {
            return;
        }
        Process process = new ProcessBuilder("git", "-C", projectDir.toString(), "ls-files")
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            int rc = process.waitFor();
            if (rc != 0) {
                throw new IllegalStateException("Unable to inspect tracked files with git: " + output.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while inspecting tracked files", e);
        }
        List<String> forbidden = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_TRACKED_PREFIXES.stream().anyMatch(lower::startsWith)
                || lower.endsWith(".sqlite")
                || lower.endsWith(".sqlite3")
                || lower.endsWith(".db")
                || lower.endsWith(".lod")
                || lower.endsWith(".log")
                || lower.endsWith(".jar")
                || lower.endsWith(".zip")) {
                if (!line.equals("gradle/wrapper/gradle-wrapper.jar")) {
                    forbidden.add(line);
                }
            }
        }
        if (!forbidden.isEmpty()) {
            throw new IllegalStateException("Generated/runtime/archive files are tracked: " + forbidden);
        }
    }

    private static void validateNoTemplatePackage(Path projectDir) throws IOException {
        Path sourceRoot = projectDir.resolve("src");
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            List<Path> forbidden = stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().replace('\\', '/').contains("com/myname/mymodid"))
                .toList();
            if (!forbidden.isEmpty()) {
                throw new IllegalStateException("Template package remains in source: " + forbidden);
            }
        }
    }
}
