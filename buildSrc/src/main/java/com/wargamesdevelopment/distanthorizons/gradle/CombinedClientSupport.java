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
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CombinedClientSupport {

    public static final String DISTANT_HORIZONS_COMMIT = "1bcabae75b3ae3160995355e1c46d94659146d0e";
    public static final String LWJGL3IFY_COMMIT = "7500f19e88a47e6ecc587f33789766bbac365d19";
    public static final String CONTRACT_VERSION = "change-006-v1";
    public static final String MANIFEST_NAME = "wdg-combined-client-manifest.json";
    public static final String RUNTIME_PACKAGED_NAME = "lwjgl3ify-wdg-java21-runtimes.zip";
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
        File lwjgl3ify,
        File gtnhLib,
        File uniMixins,
        File angelica,
        File runtimeBundle
    ) {}

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
        String sha256
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
        RuntimeArtifactVerifier.verifyRuntimeBundle(inputs.runtimeBundle());
        validateCrossJarModIds(artifacts);

        String manifest = manifestJson(definition, artifacts, inputs.runtimeBundle());
        String readme = readmeText(definition, artifacts);
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
        members.put(
            root + "lwjgl3ify/runtime/" + RUNTIME_PACKAGED_NAME,
            MemberSource.file(inputs.runtimeBundle().toPath())
        );

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
        String runtimeMember = root + "lwjgl3ify/runtime/" + RUNTIME_PACKAGED_NAME;

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
            inventory.require(runtimeMember);

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
            if (!inventory.contentEquals(runtimeMember, inputs.runtimeBundle().toPath())) {
                throw new IllegalStateException("Packaged runtime bundle differs from the normalized lwjgl3ify output");
            }
            for (String name : inventory.names()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.contains("distanthorizons-alpha18.jar")
                    || lower.contains("lwjgl3ify-3.0.15.jar")
                    || lower.contains("gtnhmixins.jar")
                    || lower.contains("-sources.jar")
                    || lower.contains("-api.jar")
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
            requireManifestField(manifest, "\"distantHorizonsCommit\": \"" + DISTANT_HORIZONS_COMMIT + "\"");
            requireManifestField(manifest, "\"lwjgl3ifyCommit\": \"" + LWJGL3IFY_COMMIT + "\"");
            requireManifestField(
                manifest,
                "\"runtimeBundleSha256\": \"" + FoundationSupport.sha256(inputs.runtimeBundle().toPath()) + "\""
            );
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
            report.put("runtimeMember", runtimeMember);
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
                inputs.distantHorizonsVersion()
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

        Map<String, Object> lwjglReport = Lwjgl3ifyCompatibilityVerifier.verify(inputs.lwjgl3ify());
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
            String.valueOf(report.get("artifactSha256"))
        );
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
        File runtimeBundle
    ) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": 1,\n");
        json.append("  \"packageType\": \"").append(json(definition.packageType())).append("\",\n");
        json.append("  \"minecraftVersion\": \"1.7.10\",\n");
        json.append("  \"forgeVersion\": \"10.13.4.1614\",\n");
        json.append("  \"distantHorizonsCommit\": \"").append(DISTANT_HORIZONS_COMMIT).append("\",\n");
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
        json.append("  \"runtimeBundleFilename\": \"").append(RUNTIME_PACKAGED_NAME).append("\",\n");
        json.append("  \"runtimeBundleSha256\": \"")
            .append(FoundationSupport.sha256(runtimeBundle.toPath()))
            .append("\",\n");
        json.append("  \"supportedJavaRuntimePlatforms\": [");
        for (int index = 0; index < RuntimeArtifactVerifier.RUNTIME_PLATFORMS.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            json.append("\"").append(RuntimeArtifactVerifier.RUNTIME_PLATFORMS.get(index)).append("\"");
        }
        json.append("]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String readmeText(PackageDefinition definition, List<ArtifactSpec> artifacts) {
        StringBuilder text = new StringBuilder();
        text.append("DistantHorizons-WDG Change 006 - ").append(definition.packageType()).append("\n\n");
        text.append("This is a clean overlay for a disposable CurseForge Minecraft 1.7.10 / Forge 10.13.4.1614 profile.\n");
        text.append("It contains no accounts, worlds, configs, logs, options, resource packs, or shader packs.\n");
        text.append("Apply this package to a fresh profile and do not use --delete when copying it.\n\n");
        text.append("Included mod JARs:\n");
        for (ArtifactSpec artifact : artifacts) {
            text.append("- ").append(artifact.packagedFilename())
                .append(" (role=").append(artifact.role())
                .append(", version=").append(artifact.version()).append(")\n");
        }
        text.append("\nPackaged Java bundle: lwjgl3ify/runtime/").append(RUNTIME_PACKAGED_NAME).append("\n");
        text.append("Review ").append(MANIFEST_NAME).append(" for exact SHA-256 identities.\n");
        return text.toString();
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
