package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReleaseCandidateSupport {

    public static final String VERSION = "3.0.4-b-wdg-rc.1";
    public static final String ROOT = "DistantHorizons-WDG-" + VERSION;
    public static final String VALIDATION_SUFFIX = "-UNCOMMITTED_VALIDATION";
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);
    private static final int RELEASE_MEMBER_COUNT = 17;
    private static final int CURSEFORGE_MEMBER_COUNT = 9;

    private ReleaseCandidateSupport() {}

    public record Inputs(
        File distantHorizons,
        File generatedRefmap,
        File lwjgl3ify,
        File gtnhLib,
        File uniMixins,
        File angelica,
        String provenanceMode,
        String expectedCommit,
        String sourceTreeDigest,
        File documentationDirectory
    ) {
        public String buildSource() {
            return "FINAL".equalsIgnoreCase(provenanceMode)
                ? ProvenanceSupport.FINAL_SOURCE : ProvenanceSupport.VALIDATION_SOURCE;
        }

        public String treeState() {
            return "FINAL".equalsIgnoreCase(provenanceMode) ? "clean" : "modified";
        }
    }

    private record Member(byte[] bytes, Path path) {
        static Member bytes(byte[] value) {
            return new Member(value, null);
        }

        static Member file(File value) {
            return new Member(null, value.toPath());
        }

        byte[] read() throws IOException {
            return bytes != null ? bytes : Files.readAllBytes(path);
        }
    }

    public static Map<String, Object> createReleaseArchive(Inputs inputs, Path output) throws IOException {
        return createReleaseArchive(inputs, output, true);
    }

    static Map<String, Object> createReleaseArchiveForTesting(Inputs inputs, Path output) throws IOException {
        return createReleaseArchive(inputs, output, false);
    }

    private static Map<String, Object> createReleaseArchive(
        Inputs inputs,
        Path output,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        CombinedClientSupport.Inputs combined = combinedInputs(inputs);
        CombinedClientSupport.PackageDefinition definition = new CombinedClientSupport.PackageDefinition(
            "release-candidate", ROOT, true, true
        );
        List<CombinedClientSupport.ArtifactSpec> artifacts = verifyAndDescribe(
            definition,
            combined,
            requireExactThirdPartyIdentity
        );

        Map<String, Member> members = new TreeMap<>();
        String root = ROOT + "/";
        addText(members, root + "README.txt", readme(inputs));
        copyDocument(members, inputs, "INSTALLATION.md");
        copyDocument(members, inputs, "UPGRADE.md");
        copyDocument(members, inputs, "KNOWN_CONFLICTS.md");
        copyDocument(members, inputs, "PERFORMANCE.md");
        copyDocument(members, inputs, "ROLLBACK.md");
        copyDocument(members, inputs, "WINDOWS_TESTING.md");
        copyDocument(members, inputs, "SERVER_COMPATIBILITY.md");

        for (CombinedClientSupport.ArtifactSpec artifact : artifacts) {
            members.put(root + "mods/" + artifact.packagedFilename(), Member.file(artifact.file()));
        }

        addText(members, root + "dependency-list.json", dependencyJson(artifacts));
        addText(members, root + "DEPENDENCIES.txt", dependencyText(artifacts));
        addText(members, root + "release-manifest.json", releaseManifest(inputs, artifacts, members));
        addText(members, root + "SHA256SUMS", checksums(root, members));

        writeZip(output, members);
        verifyReleaseArchive(inputs, output.toFile(), requireExactThirdPartyIdentity);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("archive", output.toAbsolutePath().normalize());
        report.put("archiveSize", Files.size(output));
        report.put("archiveSha256", FoundationSupport.sha256(output));
        report.put("root", ROOT);
        report.put("memberCount", members.size());
        report.put("runtimeDistributionMode", Lwjgl3ifyCompatibilityVerifier.RUNTIME_DISTRIBUTION_MODE);
        report.put("externalRuntimeBundlePresent", false);
        report.put("buildSource", inputs.buildSource());
        report.put("treeState", inputs.treeState());
        report.put("verified", true);
        return report;
    }

    public static Map<String, Object> createCurseForgeTestingProfile(Inputs inputs, Path output)
        throws IOException {
        return createCurseForgeTestingProfile(inputs, output, true);
    }

    static Map<String, Object> createCurseForgeTestingProfileForTesting(Inputs inputs, Path output)
        throws IOException {
        return createCurseForgeTestingProfile(inputs, output, false);
    }

    private static Map<String, Object> createCurseForgeTestingProfile(
        Inputs inputs,
        Path output,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        CombinedClientSupport.Inputs combined = combinedInputs(inputs);
        List<CombinedClientSupport.ArtifactSpec> artifacts = verifyAndDescribe(
            new CombinedClientSupport.PackageDefinition("curseforge-testing-profile", ROOT, true, true),
            combined,
            requireExactThirdPartyIdentity
        );
        Map<String, Member> members = new TreeMap<>();
        String manifest = "{\n"
            + "  \"minecraft\": {\"version\": \"1.7.10\", \"modLoaders\": [{\"id\": \"forge-10.13.4.1614\", \"primary\": true}]},\n"
            + "  \"manifestType\": \"minecraftModpack\",\n"
            + "  \"manifestVersion\": 1,\n"
            + "  \"name\": \"Distant Horizons WDG Release Candidate Testing\",\n"
            + "  \"version\": \"" + VERSION + "\",\n"
            + "  \"author\": \"Wargames Development Group\",\n"
            + "  \"files\": [],\n"
            + "  \"overrides\": \"overrides\"\n"
            + "}\n";
        addText(members, "manifest.json", manifest);
        for (CombinedClientSupport.ArtifactSpec artifact : artifacts) {
            members.put("overrides/mods/" + artifact.packagedFilename(), Member.file(artifact.file()));
        }
        addText(members, "overrides/config/Distant Horizons.toml", freshProfileConfig());
        addText(members, "overrides/DISTANT_HORIZONS_WDG_RC_README.txt", readme(inputs));
        addText(
            members,
            "overrides/release-manifest-reference.json",
            releaseManifest(inputs, artifacts, members)
        );
        writeZip(output, members);

        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(output.toFile())) {
            RuntimeArtifactVerifier.validateSafeArchive(inventory);
            inventory.require("manifest.json");
            inventory.require("overrides/config/Distant Horizons.toml");
            inventory.require("overrides/release-manifest-reference.json");
            if (inventory.names().size() != CURSEFORGE_MEMBER_COUNT) {
                throw new IllegalStateException(
                    "CurseForge testing-profile member count mismatch: expected="
                        + CURSEFORGE_MEMBER_COUNT + ", actual=" + inventory.names().size()
                );
            }
            for (CombinedClientSupport.ArtifactSpec artifact : artifacts) {
                String member = "overrides/mods/" + artifact.packagedFilename();
                inventory.require(member);
                if (!inventory.contentEquals(member, artifact.file().toPath())) {
                    throw new IllegalStateException("CurseForge artifact bytes differ from verified input: " + member);
                }
            }
            rejectObsoleteExternalRuntimeMembers(inventory.names());
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("profile", output.toAbsolutePath().normalize());
        report.put("profileSize", Files.size(output));
        report.put("profileSha256", FoundationSupport.sha256(output));
        report.put("memberCount", CURSEFORGE_MEMBER_COUNT);
        report.put("runtimeDistributionMode", Lwjgl3ifyCompatibilityVerifier.RUNTIME_DISTRIBUTION_MODE);
        report.put("externalRuntimeBundlePresent", false);
        report.put("structurallyVerified", true);
        report.put("actualCurseForgeImportRequired", true);
        report.put("buildSource", inputs.buildSource());
        return report;
    }

    public static Map<String, Object> verifyReproducibility(Inputs inputs, Path first, Path second)
        throws IOException {
        return verifyReproducibility(inputs, first, second, true);
    }

    static Map<String, Object> verifyReproducibilityForTesting(Inputs inputs, Path first, Path second)
        throws IOException {
        return verifyReproducibility(inputs, first, second, false);
    }

    private static Map<String, Object> verifyReproducibility(
        Inputs inputs,
        Path first,
        Path second,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        createReleaseArchive(inputs, first, requireExactThirdPartyIdentity);
        createReleaseArchive(inputs, second, requireExactThirdPartyIdentity);
        if (Files.mismatch(first, second) != -1L) {
            throw new IllegalStateException("Release-candidate archives are not byte-for-byte reproducible");
        }
        return Map.of(
            "firstSha256", FoundationSupport.sha256(first),
            "secondSha256", FoundationSupport.sha256(second),
            "reproducible", true
        );
    }

    public static void verifyReleaseArchive(Inputs inputs, File archive) throws IOException {
        verifyReleaseArchive(inputs, archive, true);
    }

    static void verifyReleaseArchiveForTesting(Inputs inputs, File archive) throws IOException {
        verifyReleaseArchive(inputs, archive, false);
    }

    private static void verifyReleaseArchive(
        Inputs inputs,
        File archive,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(archive)) {
            RuntimeArtifactVerifier.validateSafeArchive(inventory);
            if (!ROOT.equals(RuntimeArtifactVerifier.singleRoot(inventory.names()))) {
                throw new IllegalStateException("Release archive must contain exactly root " + ROOT);
            }
            if (inventory.names().size() != RELEASE_MEMBER_COUNT) {
                throw new IllegalStateException(
                    "Release archive member count mismatch: expected=" + RELEASE_MEMBER_COUNT
                        + ", actual=" + inventory.names().size()
                );
            }
            rejectObsoleteExternalRuntimeMembers(inventory.names());

            String root = ROOT + "/";
            CombinedClientSupport.Inputs combined = combinedInputs(inputs);
            List<CombinedClientSupport.ArtifactSpec> artifacts = verifyAndDescribe(
                new CombinedClientSupport.PackageDefinition("release-candidate", ROOT, true, true),
                combined,
                requireExactThirdPartyIdentity
            );
            for (CombinedClientSupport.ArtifactSpec artifact : artifacts) {
                String member = root + "mods/" + artifact.packagedFilename();
                inventory.require(member);
                if (!inventory.contentEquals(member, artifact.file().toPath())) {
                    throw new IllegalStateException("Release artifact bytes differ from verified input: " + member);
                }
            }
            for (String required : List.of(
                "README.txt", "INSTALLATION.md", "UPGRADE.md", "KNOWN_CONFLICTS.md", "PERFORMANCE.md",
                "ROLLBACK.md", "WINDOWS_TESTING.md", "SERVER_COMPATIBILITY.md", "SHA256SUMS",
                "dependency-list.json", "DEPENDENCIES.txt", "release-manifest.json"
            )) {
                inventory.require(root + required);
            }

            String releaseManifest = inventory.text(root + "release-manifest.json");
            require(releaseManifest, "\"releaseChannel\": \"RELEASE_CANDIDATE\"");
            require(releaseManifest, "\"modVersion\": \"" + VERSION + "\"");
            require(releaseManifest, "\"updaterPolicy\": \"MANAGED_DISABLED\"");
            require(releaseManifest, "\"buildSource\": \"" + inputs.buildSource() + "\"");
            require(releaseManifest, "\"sourceTreeState\": \"" + inputs.treeState() + "\"");
            require(releaseManifest, "\"sourceTreeDigest\": \"" + inputs.sourceTreeDigest() + "\"");
            require(
                releaseManifest,
                "\"runtimeDistributionMode\": \""
                    + Lwjgl3ifyCompatibilityVerifier.RUNTIME_DISTRIBUTION_MODE + "\""
            );
            Map<String, Object> lwjglReport = CombinedClientSupport.lwjgl3ifyReport(artifacts);
            for (Lwjgl3ifyCompatibilityVerifier.RuntimeArchive runtime
                : Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES) {
                require(releaseManifest, "\"id\": \"" + runtime.platformId() + "\"");
                require(releaseManifest, "\"path\": \"" + runtime.path() + "\"");
                require(
                    releaseManifest,
                    "\"sha256\": \""
                        + lwjglReport.get("embeddedRuntime." + runtime.platformId() + ".sha256") + "\""
                );
            }
            if (releaseManifest.contains("runtimeBundle")
                || releaseManifest.contains("lwjgl3ify-wdg-java21-runtimes.zip")) {
                throw new IllegalStateException("Release manifest still describes an obsolete external runtime bundle");
            }
            if (releaseManifest.contains("UNKNOWN")
                || releaseManifest.contains("NO-GIT-TAG-SET")
                || ("FINAL".equalsIgnoreCase(inputs.provenanceMode())
                    && releaseManifest.toLowerCase(Locale.ROOT).contains("dirty"))) {
                throw new IllegalStateException("Release manifest contains forbidden provenance placeholders");
            }

            String sums = inventory.text(root + "SHA256SUMS");
            for (String line : sums.lines().filter(value -> !value.isBlank()).toList()) {
                String[] parts = line.split("  ", 2);
                if (parts.length != 2) {
                    throw new IllegalStateException("Malformed SHA256SUMS line: " + line);
                }
                String member = root + parts[1];
                inventory.require(member);
                if (!FoundationSupport.sha256(inventory.bytes(member)).equals(parts[0])) {
                    throw new IllegalStateException("Checksum mismatch for " + member);
                }
            }
            for (String name : inventory.names()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.contains("launcher_accounts")
                    || lower.contains("access_token")
                    || lower.contains("servers.dat")
                    || lower.contains("options.txt")
                    || lower.contains("/saves/")
                    || lower.contains("/logs/")
                    || lower.contains("/screenshots/")
                    || lower.contains("/crash-reports/")) {
                    throw new IllegalStateException("Release archive contains user or credential data: " + name);
                }
            }
        }
    }

    private static List<CombinedClientSupport.ArtifactSpec> verifyAndDescribe(
        CombinedClientSupport.PackageDefinition definition,
        CombinedClientSupport.Inputs inputs,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        return requireExactThirdPartyIdentity
            ? CombinedClientSupport.verifyAndDescribe(definition, inputs)
            : CombinedClientSupport.verifyAndDescribeForTesting(definition, inputs);
    }

    private static CombinedClientSupport.Inputs combinedInputs(Inputs inputs) {
        return new CombinedClientSupport.Inputs(
            inputs.distantHorizons(),
            inputs.generatedRefmap(),
            VERSION,
            inputs.provenanceMode(),
            inputs.expectedCommit(),
            inputs.sourceTreeDigest(),
            inputs.lwjgl3ify(),
            inputs.gtnhLib(),
            inputs.uniMixins(),
            inputs.angelica()
        );
    }

    private static String releaseManifest(
        Inputs inputs,
        List<CombinedClientSupport.ArtifactSpec> artifacts,
        Map<String, Member> currentMembers
    ) throws IOException {
        String dhCommit;
        String buildSource;
        String treeState;
        try (FoundationSupport.ArchiveInventory jar = FoundationSupport.ArchiveInventory.open(inputs.distantHorizons())) {
            String info = jar.text("build_info.json");
            dhCommit = FoundationSupport.extractJsonString(info, "commit");
            buildSource = FoundationSupport.extractJsonString(info, "buildSource");
            treeState = FoundationSupport.extractJsonString(info, "treeState");
        }
        Map<String, Object> lwjglReport = CombinedClientSupport.lwjgl3ifyReport(artifacts);
        StringBuilder out = new StringBuilder("{\n");
        out.append("  \"schemaVersion\": 1,\n")
            .append("  \"releaseContractVersion\": \"").append(ProvenanceSupport.PACKAGE_CONTRACT).append("\",\n")
            .append("  \"packageType\": \"client-release-candidate\",\n")
            .append("  \"releaseChannel\": \"RELEASE_CANDIDATE\",\n")
            .append("  \"modVersion\": \"").append(VERSION).append("\",\n")
            .append("  \"repository\": \"").append(ProvenanceSupport.REPOSITORY).append("\",\n")
            .append("  \"distantHorizonsCommit\": \"").append(dhCommit).append("\",\n")
            .append("  \"lwjgl3ifyCommit\": \"").append(CombinedClientSupport.LWJGL3IFY_COMMIT).append("\",\n")
            .append("  \"sourceTreeState\": \"").append(treeState).append("\",\n")
            .append("  \"sourceTreeDigest\": \"").append(inputs.sourceTreeDigest()).append("\",\n")
            .append("  \"buildSource\": \"").append(buildSource).append("\",\n")
            .append("  \"minecraftVersion\": \"1.7.10\",\n")
            .append("  \"forgeVersion\": \"10.13.4.1614\",\n")
            .append("  \"updaterPolicy\": \"MANAGED_DISABLED\",\n")
            .append("  \"defaultProfile\": {\"quality\": \"LOW\", \"threadPreset\": \"MINIMAL_IMPACT\", \"lodRadiusChunks\": 128, \"generation\": true, \"generationMode\": \"SURFACE\", \"shaderPack\": null},\n")
            .append("  \"artifacts\": [\n");
        for (int index = 0; index < artifacts.size(); index++) {
            CombinedClientSupport.ArtifactSpec artifact = artifacts.get(index);
            out.append("    {\"role\": \"").append(json(artifact.role())).append("\", \"originalFilename\": \"")
                .append(json(artifact.originalFilename())).append("\", \"packagedFilename\": \"")
                .append(json(artifact.packagedFilename())).append("\", \"version\": \"")
                .append(json(artifact.version())).append("\", \"modIds\": [");
            for (int modIndex = 0; modIndex < artifact.modIds().size(); modIndex++) {
                if (modIndex > 0) {
                    out.append(", ");
                }
                out.append("\"").append(json(artifact.modIds().get(modIndex))).append("\"");
            }
            out.append("], \"size\": ").append(artifact.size()).append(", \"sha256\": \"")
                .append(artifact.sha256()).append("\", \"sourceType\": \"")
                .append(json(artifact.sourceType())).append("\", \"required\": ")
                .append(artifact.required()).append("}")
                .append(index + 1 < artifacts.size() ? "," : "")
                .append("\n");
        }
        out.append("  ],\n");
        CombinedClientSupport.appendRuntimeDistributionJson(out, lwjglReport, "  ");
        out.append(",\n  \"releaseDocumentHashes\": {\n");
        List<String> docs = currentMembers.keySet().stream()
            .filter(name -> name.endsWith(".md") || name.endsWith("README.txt"))
            .sorted()
            .toList();
        for (int index = 0; index < docs.size(); index++) {
            String name = docs.get(index);
            out.append("    \"")
                .append(json(name.startsWith(ROOT + "/") ? name.substring(ROOT.length() + 1) : name))
                .append("\": \"").append(FoundationSupport.sha256(currentMembers.get(name).read())).append("\"")
                .append(index + 1 < docs.size() ? "," : "")
                .append("\n");
        }
        out.append("  },\n  \"packageSha256\": \"RECORDED_EXTERNALLY_TO_AVOID_RECURSION\"\n}\n");
        return out.toString();
    }

    private static String dependencyJson(List<CombinedClientSupport.ArtifactSpec> artifacts) {
        StringBuilder out = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"required\": [\n");
        List<CombinedClientSupport.ArtifactSpec> required = artifacts.stream()
            .filter(CombinedClientSupport.ArtifactSpec::required)
            .toList();
        for (int index = 0; index < required.size(); index++) {
            CombinedClientSupport.ArtifactSpec artifact = required.get(index);
            out.append("    {\"role\": \"").append(json(artifact.role())).append("\", \"filename\": \"")
                .append(json(artifact.packagedFilename())).append("\", \"version\": \"")
                .append(json(artifact.version())).append("\", \"sha256\": \"")
                .append(artifact.sha256()).append("\"}")
                .append(index + 1 < required.size() ? "," : "")
                .append("\n");
        }
        out.append("  ],\n  \"includedRenderingIntegration\": [\n");
        List<CombinedClientSupport.ArtifactSpec> integrations = artifacts.stream()
            .filter(artifact -> !artifact.required())
            .toList();
        for (int index = 0; index < integrations.size(); index++) {
            CombinedClientSupport.ArtifactSpec artifact = integrations.get(index);
            out.append("    {\"role\": \"").append(json(artifact.role())).append("\", \"filename\": \"")
                .append(json(artifact.packagedFilename())).append("\", \"version\": \"")
                .append(json(artifact.version())).append("\", \"sha256\": \"")
                .append(artifact.sha256()).append("\"}")
                .append(index + 1 < integrations.size() ? "," : "")
                .append("\n");
        }
        out.append("  ],\n");
        CombinedClientSupport.appendRuntimeDistributionJson(
            out,
            CombinedClientSupport.lwjgl3ifyReport(artifacts),
            "  "
        );
        out.append(",\n  \"compositeComponentsNotAddedSeparately\": [\"GTNHMixins\", \"MCPatcherForge\", \"NotFine\", \"UniMixins submodules\", \"MixinExtras\"]\n}\n");
        return out.toString();
    }

    private static String dependencyText(List<CombinedClientSupport.ArtifactSpec> artifacts) {
        StringBuilder out = new StringBuilder("Required client stack:\n");
        artifacts.stream().filter(CombinedClientSupport.ArtifactSpec::required)
            .forEach(artifact -> out.append("- ").append(artifact.packagedFilename())
                .append(" (").append(artifact.version()).append(")\n"));
        out.append("\nPackaged Java 21 distribution:\n")
            .append("- Embedded in ").append(Lwjgl3ifyCompatibilityVerifier.EXPECTED_FILENAME).append("\n")
            .append("- Primary: Linux x86_64, macOS AArch64, macOS x86_64, Windows x86_64\n")
            .append("- Optional manual extensions: Linux AArch64 and Windows AArch64\n\n")
            .append("Included rendering integration:\n");
        artifacts.stream().filter(artifact -> !artifact.required())
            .forEach(artifact -> out.append("- ").append(artifact.packagedFilename())
                .append(" (").append(artifact.version()).append(")\n"));
        out.append("\nDo not add GTNHMixins, MCPatcherForge, NotFine, UniMixins submodules, MixinExtras, or Angelica internal components as separate JARs.\n");
        return out.toString();
    }

    private static String checksums(String root, Map<String, Member> members) throws IOException {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Member> entry : members.entrySet()) {
            if (entry.getKey().endsWith("/SHA256SUMS")) {
                continue;
            }
            out.append(FoundationSupport.sha256(entry.getValue().read())).append("  ")
                .append(entry.getKey().substring(root.length())).append('\n');
        }
        return out.toString();
    }

    private static String readme(Inputs inputs) {
        return "Distant Horizons WDG " + VERSION + "\n"
            + "RELEASE CANDIDATE — NOT A PUBLIC STABLE RELEASE\n"
            + "Build source: " + inputs.buildSource() + "\n"
            + "Updater policy: MANAGED_DISABLED (updates are delivered through WDG release assets)\n"
            + "Client-side package for Minecraft 1.7.10 / Forge 10.13.4.1614.\n"
            + "The supported primary Temurin Java 21 runtimes are embedded in the exact lwjgl3ify Change 005 JAR.\n"
            + "Linux AArch64 and Windows AArch64 are optional manual extensions, not required package inputs.\n"
            + "Read INSTALLATION.md, UPGRADE.md, PERFORMANCE.md, KNOWN_CONFLICTS.md, and ROLLBACK.md before deployment.\n";
    }

    public static String freshProfileConfig() {
        return "_version = 4\n\n"
            + "[client]\nqualityPresetSetting = \"LOW\"\nthreadPresetSetting = \"MINIMAL_IMPACT\"\n\n"
            + "[client.advanced.graphics.quality]\nlodChunkRenderDistanceRadius = 128\n\n"
            + "[common.worldGenerator]\nenableDistantGeneration = true\ndistantGeneratorMode = \"SURFACE\"\n\n"
            + "[client.advanced.autoUpdater]\nenableAutoUpdater = false\nenableSilentUpdates = false\n";
    }

    private static void copyDocument(Map<String, Member> members, Inputs inputs, String name)
        throws IOException {
        File file = new File(inputs.documentationDirectory(), name);
        FoundationSupport.requireReadableRegularFile(file, "Release document " + name);
        members.put(ROOT + "/" + name, Member.file(file));
    }

    private static void addText(Map<String, Member> members, String name, String text) {
        members.put(name, Member.bytes(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static void writeZip(Path output, Map<String, Member> members) throws IOException {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        Files.deleteIfExists(output);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            zip.setLevel(0);
            for (Map.Entry<String, Member> entry : members.entrySet()) {
                byte[] bytes = entry.getValue().read();
                CRC32 crc = new CRC32();
                crc.update(bytes);
                ZipEntry member = new ZipEntry(entry.getKey());
                member.setMethod(ZipEntry.STORED);
                member.setSize(bytes.length);
                member.setCompressedSize(bytes.length);
                member.setCrc(crc.getValue());
                member.setTimeLocal(FIXED_TIME);
                member.setExtra(new byte[0]);
                zip.putNextEntry(member);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
    }

    private static void rejectObsoleteExternalRuntimeMembers(List<String> names) {
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("lwjgl3ify-wdg-java21-runtimes.zip")
                || lower.contains("bundled-client")
                || lower.contains("/lwjgl3ify/runtime/")) {
                throw new IllegalStateException("Obsolete split-runtime package member rejected: " + name);
            }
        }
    }

    private static void require(String text, String marker) {
        if (!text.contains(marker)) {
            throw new IllegalStateException("Release manifest is missing " + marker);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
