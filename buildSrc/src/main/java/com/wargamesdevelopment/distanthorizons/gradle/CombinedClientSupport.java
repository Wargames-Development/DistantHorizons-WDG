package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CombinedClientSupport {

    public static final String LWJGL3IFY_COMMIT = "d7e60f5a0dea4aa348e3c06b8f0a87c171522a37";
    public static final String CONTRACT_VERSION = ProvenanceSupport.PACKAGE_CONTRACT;
    public static final String MANIFEST_NAME = "wdg-combined-client-manifest.json";
    private static final LocalDateTime FIXED_ZIP_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);

    private CombinedClientSupport() {}

    public record PackageDefinition(
        String packageType,
        String rootDirectory,
        boolean includeDistantHorizons,
        boolean includeAngelica
    ) {
        public PackageDefinition {
            if (packageType == null || packageType.isBlank()) {
                throw new IllegalArgumentException("packageType is empty");
            }
            if (rootDirectory == null || rootDirectory.isBlank() || rootDirectory.contains("/")) {
                throw new IllegalArgumentException("rootDirectory must be one safe path segment");
            }
        }

        public int expectedModCount() {
            return 3 + (includeDistantHorizons ? 1 : 0) + (includeAngelica ? 1 : 0);
        }
    }

    public record Inputs(
        File distantHorizons,
        File generatedRefmap,
        String distantHorizonsVersion,
        String provenanceMode,
        String expectedCommit,
        String sourceTreeDigest,
        File lwjgl3ify,
        File gtnhLib,
        File uniMixins,
        File angelica
    ) {
        public Inputs(
            File distantHorizons,
            File generatedRefmap,
            String distantHorizonsVersion,
            File lwjgl3ify,
            File gtnhLib,
            File uniMixins,
            File angelica
        ) {
            this(
                distantHorizons,
                generatedRefmap,
                distantHorizonsVersion,
                "VALIDATION",
                ProvenanceSupport.BASE_COMMIT,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                lwjgl3ify,
                gtnhLib,
                uniMixins,
                angelica
            );
        }

        public Inputs {
            provenanceMode = provenanceMode == null ? "VALIDATION" : provenanceMode;
            expectedCommit = expectedCommit == null ? ProvenanceSupport.BASE_COMMIT : expectedCommit;
            if (sourceTreeDigest == null || !sourceTreeDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sourceTreeDigest must be a lowercase SHA-256");
            }
        }

        public String buildSource() {
            return provenanceMode.equalsIgnoreCase("FINAL")
                ? ProvenanceSupport.FINAL_SOURCE
                : ProvenanceSupport.VALIDATION_SOURCE;
        }

        public String treeState() {
            return provenanceMode.equalsIgnoreCase("FINAL") ? "clean" : "modified";
        }
    }

    public record ArtifactSpec(
        String role,
        String sourceType,
        boolean required,
        String originalFilename,
        String packagedFilename,
        String version,
        List<String> modIds,
        File file,
        long size,
        String sha256,
        Map<String, Object> verification
    ) {}

    private record MemberSource(byte[] inlineBytes, Path sourceFile) {

        private MemberSource {
            if ((inlineBytes == null) == (sourceFile == null)) {
                throw new IllegalArgumentException("Exactly one package-member source must be set");
            }
        }

        static MemberSource inline(byte[] bytes) {
            return new MemberSource(bytes, null);
        }

        static MemberSource file(Path path) {
            return new MemberSource(null, path);
        }
    }

    public static Map<String, Object> createPackage(
        PackageDefinition definition,
        Inputs inputs,
        Path output
    ) throws IOException {
        return createPackage(definition, inputs, output, true);
    }

    static Map<String, Object> createPackageForTesting(
        PackageDefinition definition,
        Inputs inputs,
        Path output
    ) throws IOException {
        return createPackage(definition, inputs, output, false);
    }

    private static Map<String, Object> createPackage(
        PackageDefinition definition,
        Inputs inputs,
        Path output,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        List<ArtifactSpec> artifacts = verifyAndDescribe(definition, inputs, requireExactThirdPartyIdentity);
        validateCrossJarModIds(artifacts);

        String manifest = manifestJson(definition, artifacts, inputs);
        String readme = readmeText(definition, artifacts, inputs);
        Map<String, MemberSource> members = new TreeMap<>();
        String root = definition.rootDirectory() + "/";
        members.put(root + "README.txt", MemberSource.inline(readme.getBytes(StandardCharsets.UTF_8)));
        members.put(root + MANIFEST_NAME, MemberSource.inline(manifest.getBytes(StandardCharsets.UTF_8)));
        for (ArtifactSpec artifact : artifacts) {
            members.put(
                root + "mods/" + artifact.packagedFilename(),
                MemberSource.file(artifact.file().toPath())
            );
        }
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        Files.deleteIfExists(output);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            zip.setLevel(0);
            for (Map.Entry<String, MemberSource> member : members.entrySet()) {
                writeStored(zip, member.getKey(), member.getValue());
            }
        }

        Map<String, Object> report = verifyPackage(
            definition,
            inputs,
            output.toFile(),
            requireExactThirdPartyIdentity
        );
        report.put("packagePath", output.toAbsolutePath().normalize());
        report.put("packageSize", Files.size(output));
        report.put("packageSha256", FoundationSupport.sha256(output));
        return report;
    }

    public static Map<String, Object> verifyPackage(
        PackageDefinition definition,
        Inputs inputs,
        File packageFile
    ) throws IOException {
        return verifyPackage(definition, inputs, packageFile, true);
    }

    static Map<String, Object> verifyPackageForTesting(
        PackageDefinition definition,
        Inputs inputs,
        File packageFile
    ) throws IOException {
        return verifyPackage(definition, inputs, packageFile, false);
    }

    private static Map<String, Object> verifyPackage(
        PackageDefinition definition,
        Inputs inputs,
        File packageFile,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        List<ArtifactSpec> artifacts = verifyAndDescribe(definition, inputs, requireExactThirdPartyIdentity);
        Map<String, ArtifactSpec> expectedByMember = new LinkedHashMap<>();
        String root = definition.rootDirectory() + "/";
        for (ArtifactSpec artifact : artifacts) {
            expectedByMember.put(root + "mods/" + artifact.packagedFilename(), artifact);
        }

        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(packageFile)) {
            RuntimeArtifactVerifier.validateSafeArchive(inventory);
            String actualRoot = RuntimeArtifactVerifier.singleRoot(inventory.names());
            if (!definition.rootDirectory().equals(actualRoot)) {
                throw new IllegalStateException(
                    "Package root mismatch: expected=" + definition.rootDirectory() + ", actual=" + actualRoot
                );
            }
            inventory.require(root + "README.txt");
            inventory.require(root + MANIFEST_NAME);

            List<String> modMembers = inventory.names().stream()
                .filter(name -> name.startsWith(root + "mods/") && name.endsWith(".jar"))
                .toList();
            if (modMembers.size() != definition.expectedModCount()) {
                throw new IllegalStateException(
                    "Package mod count mismatch: expected=" + definition.expectedModCount() + ", actual=" + modMembers
                );
            }
            if (!new LinkedHashSet<>(modMembers).equals(expectedByMember.keySet())) {
                throw new IllegalStateException(
                    "Package mod membership mismatch: expected=" + expectedByMember.keySet() + ", actual=" + modMembers
                );
            }
            for (Map.Entry<String, ArtifactSpec> expected : expectedByMember.entrySet()) {
                if (!inventory.contentEquals(expected.getKey(), expected.getValue().file().toPath())) {
                    throw new IllegalStateException(
                        "Packaged artifact differs from independently verified bytes: " + expected.getKey()
                    );
                }
            }
            for (String name : inventory.names()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.contains("distanthorizons-alpha18.jar")
                    || lower.contains("lwjgl3ify-3.0.15.jar")
                    || lower.contains("gtnhmixins.jar")
                    || lower.contains("-sources.jar")
                    || lower.contains("-api.jar")
                    || lower.contains("lwjgl3ify-wdg-java21-runtimes.zip")
                    || lower.startsWith(root.toLowerCase(Locale.ROOT) + "lwjgl3ify/runtime/")
                    || (lower.startsWith(root.toLowerCase(Locale.ROOT) + "mods/")
                        && (lower.endsWith(".zip") || lower.endsWith(".tar.gz")))) {
                    throw new IllegalStateException("Forbidden package member: " + name);
                }
                String relative = name.substring(root.length()).toLowerCase(Locale.ROOT);
                for (String forbidden : List.of(
                    "saves/", "world/", "worlds/", "config/", "logs/", "crash-reports/",
                    "screenshots/", "resourcepacks/", "shaderpacks/", "options.txt", "servers.dat",
                    "launcher_accounts.json", "accounts.json", ".git/", ".gradle/", "build/"
                )) {
                    if (relative.startsWith(forbidden)) {
                        throw new IllegalStateException("Package contains forbidden profile/user data: " + name);
                    }
                }
            }

            String manifest = inventory.text(root + MANIFEST_NAME);
            requireManifestField(manifest, "\"schemaVersion\": 1");
            requireManifestField(manifest, "\"packageType\": \"" + definition.packageType() + "\"");
            requireManifestField(manifest, "\"packageRoot\": \"" + definition.rootDirectory() + "\"");
            requireManifestField(manifest, "\"releaseChannel\": \"RELEASE_CANDIDATE\"");
            requireManifestField(manifest, "\"modVersion\": \"" + inputs.distantHorizonsVersion() + "\"");
            requireManifestField(manifest, "\"sourceTreeState\": \"" + inputs.treeState() + "\"");
            requireManifestField(manifest, "\"sourceTreeDigest\": \"" + inputs.sourceTreeDigest() + "\"");
            requireManifestField(manifest, "\"buildSource\": \"" + inputs.buildSource() + "\"");
            requireManifestField(manifest, "\"updaterPolicy\": \"MANAGED_DISABLED\"");
            if (definition.includeDistantHorizons()) {
                requireManifestField(manifest, "\"distantHorizonsCommit\": \"" + distantHorizonsCommit(inputs.distantHorizons()) + "\"");
            }
            requireManifestField(manifest, "\"lwjgl3ifyCommit\": \"" + LWJGL3IFY_COMMIT + "\"");
            Map<String, Object> lwjglReport = lwjgl3ifyReport(artifacts);
            requireManifestField(manifest, "\"runtimeDistributionMode\": \""
                + lwjglReport.get("runtimeDistributionMode") + "\"");
            requireManifestField(manifest, "\"javaRuntimeVersion\": \""
                + lwjglReport.get("javaRuntimeVersion") + "\"");
            for (Lwjgl3ifyCompatibilityVerifier.RuntimeArchive runtime
                : Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES) {
                requireManifestField(manifest, "\"id\": \"" + runtime.platformId() + "\"");
                requireManifestField(manifest, "\"path\": \"" + runtime.path() + "\"");
                requireManifestField(manifest, "\"sha256\": \""
                    + lwjglReport.get("embeddedRuntime." + runtime.platformId() + ".sha256") + "\"");
            }
            if (manifest.contains("runtimeBundleFilename")
                || manifest.contains("runtimeBundleSha256")
                || manifest.contains("lwjgl3ify-wdg-java21-runtimes.zip")) {
                throw new IllegalStateException("Package manifest still describes the obsolete split-runtime contract");
            }
            for (ArtifactSpec artifact : artifacts) {
                requireManifestField(manifest, "\"packagedFilename\": \"" + artifact.packagedFilename() + "\"");
                requireManifestField(manifest, "\"sha256\": \"" + artifact.sha256() + "\"");
                requireManifestField(manifest, "\"size\": " + artifact.size());
            }
            rejectMachineSpecificManifestData(manifest);
            validateCrossJarModIds(artifacts);

            List<String> orderedNames = new ArrayList<>(inventory.names());
            List<String> sortedNames = new ArrayList<>(orderedNames);
            sortedNames.sort(Comparator.naturalOrder());
            if (!orderedNames.equals(sortedNames)) {
                throw new IllegalStateException("Package ZIP members are not in deterministic lexical order");
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("packageName", packageFile.getName());
            report.put("packageSize", Files.size(packageFile.toPath()));
            report.put("packageSha256", FoundationSupport.sha256(packageFile.toPath()));
            report.put("packageType", definition.packageType());
            report.put("rootDirectory", actualRoot);
            report.put("memberCount", inventory.names().size());
            report.put("modJarCount", modMembers.size());
            report.put("modJarMembers", String.join(",", modMembers));
            report.put("runtimeDistributionMode", lwjglReport.get("runtimeDistributionMode"));
            report.put("embeddedRuntimeCount", lwjglReport.get("embeddedRuntimeCount"));
            report.put("javaRuntimeVersion", lwjglReport.get("javaRuntimeVersion"));
            report.put("externalRuntimeBundlePresent", false);
            report.put("verified", true);
            return report;
        }
    }

    public static Map<String, Object> verifyReproducibility(
        PackageDefinition definition,
        Inputs inputs,
        Path first,
        Path second
    ) throws IOException {
        return verifyReproducibility(definition, inputs, first, second, true);
    }

    static Map<String, Object> verifyReproducibilityForTesting(
        PackageDefinition definition,
        Inputs inputs,
        Path first,
        Path second
    ) throws IOException {
        return verifyReproducibility(definition, inputs, first, second, false);
    }

    private static Map<String, Object> verifyReproducibility(
        PackageDefinition definition,
        Inputs inputs,
        Path first,
        Path second,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        createPackage(definition, inputs, first, requireExactThirdPartyIdentity);
        createPackage(definition, inputs, second, requireExactThirdPartyIdentity);
        String firstHash = FoundationSupport.sha256(first);
        String secondHash = FoundationSupport.sha256(second);
        if (!firstHash.equals(secondHash)) {
            throw new IllegalStateException(
                "Combined-client package is not reproducible: first=" + firstHash + ", second=" + secondHash
            );
        }
        if (Files.mismatch(first, second) != -1L) {
            throw new IllegalStateException("Equal package hashes unexpectedly had different bytes");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("firstPackage", first.toAbsolutePath().normalize());
        report.put("secondPackage", second.toAbsolutePath().normalize());
        report.put("firstSha256", firstHash);
        report.put("secondSha256", secondHash);
        report.put("reproducible", true);
        return report;
    }

    public static List<ArtifactSpec> verifyAndDescribe(PackageDefinition definition, Inputs inputs)
        throws IOException {
        return verifyAndDescribe(definition, inputs, true);
    }

    static List<ArtifactSpec> verifyAndDescribeForTesting(PackageDefinition definition, Inputs inputs)
        throws IOException {
        return verifyAndDescribe(definition, inputs, false);
    }

    private static List<ArtifactSpec> verifyAndDescribe(
        PackageDefinition definition,
        Inputs inputs,
        boolean requireExactThirdPartyIdentity
    ) throws IOException {
        List<ArtifactSpec> artifacts = new ArrayList<>();
        if (definition.includeDistantHorizons()) {
            if (inputs.distantHorizons() == null || inputs.generatedRefmap() == null) {
                throw new IllegalStateException("Distant Horizons and its generated refmap are required for this package");
            }
            Map<String, Object> report = DistantHorizonsArtifactVerifier.verify(
                inputs.distantHorizons(),
                inputs.generatedRefmap(),
                inputs.distantHorizonsVersion(),
                inputs.provenanceMode(),
                inputs.expectedCommit()
            );
            artifacts.add(spec(
                "distant-horizons",
                "project-production-reobf",
                true,
                inputs.distantHorizons(),
                inputs.distantHorizons().getName(),
                inputs.distantHorizonsVersion(),
                List.of("distanthorizons"),
                report
            ));
        }

        Map<String, Object> lwjglReport = requireExactThirdPartyIdentity
            ? Lwjgl3ifyCompatibilityVerifier.verify(inputs.lwjgl3ify())
            : Lwjgl3ifyCompatibilityVerifier.verifyFixture(inputs.lwjgl3ify());
        artifacts.add(spec(
            "lwjgl3ify",
            "external-production-reobf",
            true,
            inputs.lwjgl3ify(),
            inputs.lwjgl3ify().getName(),
            String.valueOf(lwjglReport.get("implementationVersion")),
            List.of(String.valueOf(lwjglReport.get("publicModId"))),
            lwjglReport
        ));

        Map<String, Object> gtnhReport = requireExactThirdPartyIdentity
            ? RuntimeArtifactVerifier.verifyGtnhLib(inputs.gtnhLib())
            : RuntimeArtifactVerifier.verifyGtnhLibFixture(inputs.gtnhLib());
        artifacts.add(spec(
            "gtnhlib",
            "source-built-production-reobf",
            true,
            inputs.gtnhLib(),
            RuntimeArtifactVerifier.GTNHLIB_FILENAME,
            RuntimeArtifactVerifier.GTNHLIB_VERSION,
            splitModIds(gtnhReport),
            gtnhReport
        ));

        Map<String, Object> uniReport = requireExactThirdPartyIdentity
            ? RuntimeArtifactVerifier.verifyUniMixins(inputs.uniMixins())
            : RuntimeArtifactVerifier.verifyUniMixinsFixture(inputs.uniMixins());
        artifacts.add(spec(
            "unimixins",
            "external-production-composite",
            true,
            inputs.uniMixins(),
            RuntimeArtifactVerifier.UNIMIXINS_FILENAME,
            RuntimeArtifactVerifier.UNIMIXINS_VERSION,
            splitModIds(uniReport),
            uniReport
        ));

        if (definition.includeAngelica()) {
            if (inputs.angelica() == null) {
                throw new IllegalStateException("Angelica is required for the full combined-client package");
            }
            Map<String, Object> angelicaReport = requireExactThirdPartyIdentity
                ? RuntimeArtifactVerifier.verifyAngelica(inputs.angelica())
                : RuntimeArtifactVerifier.verifyAngelicaFixture(inputs.angelica());
            artifacts.add(spec(
                "angelica",
                "external-production-composite",
                false,
                inputs.angelica(),
                RuntimeArtifactVerifier.ANGELICA_FILENAME,
                RuntimeArtifactVerifier.ANGELICA_VERSION,
                splitModIds(angelicaReport),
                angelicaReport
            ));
        }
        artifacts.sort(Comparator.comparing(ArtifactSpec::packagedFilename));
        return List.copyOf(artifacts);
    }

    private static ArtifactSpec spec(
        String role,
        String sourceType,
        boolean required,
        File file,
        String packagedFilename,
        String version,
        List<String> modIds,
        Map<String, Object> report
    ) {
        return new ArtifactSpec(
            role,
            sourceType,
            required,
            file.getName(),
            packagedFilename,
            version,
            List.copyOf(modIds),
            file,
            Long.parseLong(String.valueOf(report.get("artifactSize"))),
            String.valueOf(report.get("artifactSha256")),
            Map.copyOf(report)
        );
    }


    private static String distantHorizonsCommit(File artifact) throws IOException {
        if (artifact == null) {
            throw new IllegalStateException("Distant Horizons artifact is required to resolve package provenance");
        }
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(artifact)) {
            return FoundationSupport.extractJsonString(inventory.text("build_info.json"), "commit");
        }
    }

    private static List<String> splitModIds(Map<String, Object> report) {
        return Arrays.stream(String.valueOf(report.get("modIds")).split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    private static void validateCrossJarModIds(List<ArtifactSpec> artifacts) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (ArtifactSpec artifact : artifacts) {
            for (String modId : artifact.modIds()) {
                String previous = owners.putIfAbsent(modId.toLowerCase(Locale.ROOT), artifact.packagedFilename());
                if (previous != null && !previous.equals(artifact.packagedFilename())) {
                    throw new IllegalStateException(
                        "Duplicate mod ID across separate JARs: " + modId + " in " + previous
                            + " and " + artifact.packagedFilename()
                    );
                }
            }
        }
    }

    private static String manifestJson(
        PackageDefinition definition,
        List<ArtifactSpec> artifacts,
        Inputs inputs
    ) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": 1,\n");
        json.append("  \"packageType\": \"").append(json(definition.packageType())).append("\",\n");
        json.append("  \"minecraftVersion\": \"1.7.10\",\n");
        json.append("  \"forgeVersion\": \"10.13.4.1614\",\n");
        json.append("  \"releaseContractVersion\": \"change-007-rc-v1\",\n");
        json.append("  \"releaseChannel\": \"RELEASE_CANDIDATE\",\n");
        json.append("  \"modVersion\": \"").append(json(inputs.distantHorizonsVersion())).append("\",\n");
        json.append("  \"repository\": \"Wargames-Development/DistantHorizons-WDG\",\n");
        json.append("  \"sourceTreeState\": \"").append(inputs.treeState()).append("\",\n");
        json.append("  \"sourceTreeDigest\": \"").append(inputs.sourceTreeDigest()).append("\",\n");
        json.append("  \"buildSource\": \"").append(inputs.buildSource()).append("\",\n");
        json.append("  \"updaterPolicy\": \"MANAGED_DISABLED\",\n");
        if (definition.includeDistantHorizons()) {
            json.append("  \"distantHorizonsCommit\": \"").append(distantHorizonsCommit(inputs.distantHorizons())).append("\",\n");
        } else {
            json.append("  \"distantHorizonsCommit\": null,\n");
        }
        json.append("  \"lwjgl3ifyCommit\": \"").append(LWJGL3IFY_COMMIT).append("\",\n");
        json.append("  \"packageCreationContract\": \"").append(CONTRACT_VERSION).append("\",\n");
        json.append("  \"packageRoot\": \"").append(json(definition.rootDirectory())).append("\",\n");
        json.append("  \"artifacts\": [\n");
        for (int index = 0; index < artifacts.size(); index++) {
            ArtifactSpec artifact = artifacts.get(index);
            json.append("    {\n");
            json.append("      \"role\": \"").append(json(artifact.role())).append("\",\n");
            json.append("      \"sourceType\": \"").append(json(artifact.sourceType())).append("\",\n");
            json.append("      \"required\": ").append(artifact.required()).append(",\n");
            json.append("      \"originalFilename\": \"").append(json(artifact.originalFilename())).append("\",\n");
            json.append("      \"packagedFilename\": \"").append(json(artifact.packagedFilename())).append("\",\n");
            json.append("      \"version\": \"").append(json(artifact.version())).append("\",\n");
            json.append("      \"modIds\": [");
            for (int modIndex = 0; modIndex < artifact.modIds().size(); modIndex++) {
                if (modIndex > 0) {
                    json.append(", ");
                }
                json.append("\"").append(json(artifact.modIds().get(modIndex))).append("\"");
            }
            json.append("],\n");
            json.append("      \"size\": ").append(artifact.size()).append(",\n");
            json.append("      \"sha256\": \"").append(artifact.sha256()).append("\"\n");
            json.append("    }").append(index + 1 < artifacts.size() ? "," : "").append("\n");
        }
        json.append("  ],\n");
        appendRuntimeDistributionJson(json, lwjgl3ifyReport(artifacts), "  ");
        json.append("\n");
        json.append("}\n");
        return json.toString();
    }

    private static String readmeText(PackageDefinition definition, List<ArtifactSpec> artifacts, Inputs inputs) {
        StringBuilder text = new StringBuilder();
        text.append("DistantHorizons-WDG Change 007 - ").append(definition.packageType()).append("\n\n");
        text.append("Version: ").append(inputs.distantHorizonsVersion()).append("\n");
        text.append("Release channel: ").append(ProvenanceSupport.RELEASE_CHANNEL).append("\n");
        text.append("Build source: ").append(inputs.buildSource()).append("\n");
        text.append("Source state: ").append(inputs.treeState()).append("\n");
        text.append("Source commit: ").append(inputs.expectedCommit()).append("\n\n");
        text.append("This is a clean overlay for a disposable CurseForge Minecraft 1.7.10 / Forge 10.13.4.1614 profile.\n");
        text.append("It contains no accounts, worlds, configs, logs, options, resource packs, or shader packs.\n");
        text.append("Apply this package to a fresh profile and do not use --delete when copying it.\n\n");
        text.append("Included mod JARs:\n");
        for (ArtifactSpec artifact : artifacts) {
            text.append("- ").append(artifact.packagedFilename())
                .append(" (role=").append(artifact.role())
                .append(", version=").append(artifact.version()).append(")\n");
        }
        text.append("\nPackaged Java distribution: embedded in ")
            .append(Lwjgl3ifyCompatibilityVerifier.EXPECTED_FILENAME).append("\n");
        text.append("Primary platforms: Linux x86_64, macOS AArch64, macOS x86_64, Windows x86_64.\n");
        text.append("Linux AArch64 and Windows AArch64 are optional manual extension assets and are not included.\n");
        text.append("Review ").append(MANIFEST_NAME).append(" for exact embedded-runtime SHA-256 identities.\n");
        return text.toString();
    }

    static Map<String, Object> lwjgl3ifyReport(List<ArtifactSpec> artifacts) {
        return artifacts.stream()
            .filter(artifact -> "lwjgl3ify".equals(artifact.role()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Verified lwjgl3ify artifact is missing"))
            .verification();
    }

    static void appendRuntimeDistributionJson(
        StringBuilder json,
        Map<String, Object> report,
        String indent
    ) {
        json.append(indent).append("\"lwjgl3ifyRuntimeDistribution\": {\n");
        json.append(indent).append("  \"commit\": \"")
            .append(LWJGL3IFY_COMMIT).append("\",\n");
        json.append(indent).append("  \"artifactFilename\": \"")
            .append(json(String.valueOf(report.get("artifactName")))).append("\",\n");
        json.append(indent).append("  \"artifactSize\": ")
            .append(report.get("artifactSize")).append(",\n");
        json.append(indent).append("  \"artifactSha256\": \"")
            .append(report.get("artifactSha256")).append("\",\n");
        json.append(indent).append("  \"productionIdentity\": \"")
            .append(report.get("productionIdentity")).append("\",\n");
        json.append(indent).append("  \"runtimeDistributionMode\": \"")
            .append(report.get("runtimeDistributionMode")).append("\",\n");
        json.append(indent).append("  \"javaRuntimeVersion\": \"")
            .append(report.get("javaRuntimeVersion")).append("\",\n");
        json.append(indent).append("  \"embeddedPrimaryPlatforms\": [\n");
        for (int index = 0; index < Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES.size(); index++) {
            Lwjgl3ifyCompatibilityVerifier.RuntimeArchive runtime =
                Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES.get(index);
            json.append(indent).append("    {\"id\": \"").append(runtime.platformId())
                .append("\", \"path\": \"").append(runtime.path())
                .append("\", \"size\": ")
                .append(report.get("embeddedRuntime." + runtime.platformId() + ".size"))
                .append(", \"sha256\": \"")
                .append(report.get("embeddedRuntime." + runtime.platformId() + ".sha256"))
                .append("\"}")
                .append(index + 1 < Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES.size() ? "," : "")
                .append("\n");
        }
        json.append(indent).append("  ],\n");
        json.append(indent).append("  \"optionalManualExtensions\": [\n");
        for (int index = 0; index < Lwjgl3ifyCompatibilityVerifier.OPTIONAL_EXTENSIONS.size(); index++) {
            Lwjgl3ifyCompatibilityVerifier.OptionalRuntimeExtension extension =
                Lwjgl3ifyCompatibilityVerifier.OPTIONAL_EXTENSIONS.get(index);
            json.append(indent).append("    {\"id\": \"").append(extension.platformId())
                .append("\", \"filename\": \"").append(extension.filename())
                .append("\", \"size\": ").append(extension.size())
                .append(", \"sha256\": \"").append(extension.sha256())
                .append("\", \"included\": false, \"required\": false}")
                .append(index + 1 < Lwjgl3ifyCompatibilityVerifier.OPTIONAL_EXTENSIONS.size() ? "," : "")
                .append("\n");
        }
        json.append(indent).append("  ]\n");
        json.append(indent).append("}");
    }

    private static void writeStored(ZipOutputStream zip, String name, MemberSource source) throws IOException {
        long size;
        long crcValue;
        if (source.inlineBytes() != null) {
            CRC32 crc = new CRC32();
            crc.update(source.inlineBytes());
            size = source.inlineBytes().length;
            crcValue = crc.getValue();
        } else {
            size = Files.size(source.sourceFile());
            crcValue = crc32(source.sourceFile());
        }

        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(size);
        entry.setCompressedSize(size);
        entry.setCrc(crcValue);
        entry.setTimeLocal(FIXED_ZIP_TIME);
        entry.setComment(null);
        entry.setExtra(null);
        zip.putNextEntry(entry);
        if (source.inlineBytes() != null) {
            zip.write(source.inlineBytes());
        } else {
            Files.copy(source.sourceFile(), zip);
        }
        zip.closeEntry();
    }

    private static long crc32(Path path) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                crc.update(buffer, 0, read);
            }
        }
        return crc.getValue();
    }

    private static void requireManifestField(String manifest, String marker) {
        if (!manifest.contains(marker)) {
            throw new IllegalStateException("Package manifest is missing or mismatches: " + marker);
        }
    }

    private static void rejectMachineSpecificManifestData(String manifest) {
        String lower = manifest.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
            "/users/", "\\\\users\\", "/home/", "file:/", "temporary", "tmp/", "launcher_accounts",
            "access_token", "refresh_token"
        )) {
            if (lower.contains(forbidden)) {
                throw new IllegalStateException("Package manifest contains machine-specific or private data: " + forbidden);
            }
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}
