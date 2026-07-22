package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DistantHorizonsArtifactVerifier {

    private static final List<String> REQUIRED_MEMBERS = List.of(
        "mcmod.info",
        FoundationSupport.ACCESS_TRANSFORMER,
        FoundationSupport.NORMAL_MIXIN_CONFIG,
        FoundationSupport.EARLY_MIXIN_CONFIG,
        FoundationSupport.REFMAP,
        FoundationSupport.EARLY_LOADER,
        "com/seibel/distanthorizons/forge/ForgeMain.class",
        "com/seibel/distanthorizons/coreapi/ModInfo.class",
        "com/seibel/distanthorizons/api/DhApi.class",
        "com/seibel/distanthorizons/mixin/MixinEntityRenderer.class",
        "com/seibel/distanthorizons/mixin/MixinMinecraft.class",
        "sqlScripts/scriptList.txt"
    );
    private static final List<String> SHADOWED_PREFIXES = List.of(
        "com/electronwill/nightconfig/",
        "net/jpountz/lz4/",
        "net/jpountz/xxhash/",
        "org/tukaani/xz/",
        "org/sqlite/",
        "com/github/luben/zstd/"
    );
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
        "me/eigenraven/lwjgl3ify/",
        "com/gtnewhorizon/gtnhlib/",
        "com/gtnewhorizon/gtnhmixins/",
        "com/mitchej123/hodgepodge/",
        "gregtech/",
        "com/falsepattern/rple/",
        "com/gtnewhorizons/angelica/",
        "net/minecraft/",
        "cpw/mods/fml/"
    );
    private static final Pattern REFMAP_OWNER = Pattern.compile(
        "\"(com/seibel/distanthorizons/mixin/[^\"]+)\"\\s*:\\s*\\{"
    );
    private static final List<String> OPTIONAL_MOD_NAMES = List.of(
        "hodgepodge",
        "notenoughitems",
        "angelica",
        "gregtech",
        "rple"
    );

    private DistantHorizonsArtifactVerifier() {}

    public static Map<String, Object> verify(File artifact, File generatedRefmap, String expectedVersion)
        throws IOException {
        expectedVersion = FoundationSupport.validateVersion(expectedVersion);
        validateCandidateName(artifact.getName(), expectedVersion);
        FoundationSupport.requireReadableRegularFile(artifact, "Production Distant Horizons JAR");
        FoundationSupport.requireReadableRegularFile(generatedRefmap, "Generated Distant Horizons Mixin refmap");

        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(artifact)) {
            for (String required : REQUIRED_MEMBERS) {
                inventory.require(required);
            }
            for (String prefix : SHADOWED_PREFIXES) {
                inventory.requirePrefix(prefix);
            }
            inventory.requirePrefix("org/sqlite/native/");
            List<String> sqliteNatives = inventory.names().stream()
                .filter(name -> name.startsWith("org/sqlite/native/") && !name.endsWith("/"))
                .toList();
            List<String> zstdNatives = inventory.names().stream()
                .filter(name -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.contains("libzstd-jni")
                        && (lower.endsWith(".so") || lower.endsWith(".dll") || lower.endsWith(".dylib"));
                })
                .toList();
            if (sqliteNatives.size() < 3) {
                throw new IllegalStateException("SQLite JDBC native resources are incomplete: " + sqliteNatives);
            }
            if (zstdNatives.size() < 3) {
                throw new IllegalStateException("Zstandard JNI native resources are incomplete: " + zstdNatives);
            }

            List<String> refmaps = inventory.namesEndingWith(".refmap.json");
            if (!refmaps.equals(List.of(FoundationSupport.REFMAP))) {
                throw new IllegalStateException(
                    "Production artifact must contain exactly one stable Mixin refmap; found " + refmaps
                );
            }
            byte[] artifactRefmap = inventory.bytes(FoundationSupport.REFMAP);
            byte[] generatedRefmapBytes = Files.readAllBytes(generatedRefmap.toPath());
            if (!java.util.Arrays.equals(artifactRefmap, generatedRefmapBytes)) {
                throw new IllegalStateException(
                    "Production Mixin refmap is stale: artifactSha256=" + FoundationSupport.sha256(artifactRefmap)
                        + ", generatedSha256=" + FoundationSupport.sha256(generatedRefmapBytes)
                );
            }
            String refmapText = new String(artifactRefmap, StandardCharsets.UTF_8);
            Set<String> mappedMixinOwners = verifyProductionRefmap(refmapText);
            for (String config : List.of(FoundationSupport.NORMAL_MIXIN_CONFIG, FoundationSupport.EARLY_MIXIN_CONFIG)) {
                String configText = inventory.text(config);
                if (!configText.contains("\"refmap\": \"" + FoundationSupport.REFMAP + "\"")) {
                    throw new IllegalStateException(config + " does not reference the stable production refmap");
                }
            }

            String modInfoClassName = "com/seibel/distanthorizons/coreapi/ModInfo.class";
            String generatedTokenClassName = "com/seibel/distanthorizons/coreapi/Tags.class";
            String modInfoBinaryText = new String(
                inventory.bytes(modInfoClassName),
                StandardCharsets.ISO_8859_1
            );
            if (!modInfoBinaryText.contains(expectedVersion)) {
                throw new IllegalStateException(
                    "Production ModInfo.class does not contain the unified version constant: " + expectedVersion
                );
            }
            boolean generatedTokenClassPackaged = inventory.contains(generatedTokenClassName);

            String mcmod = inventory.text("mcmod.info");
            FoundationSupport.rejectTemplateMarkers(mcmod, "Production mcmod.info");
            FoundationSupport.requireVersionMatch(
                expectedVersion,
                FoundationSupport.extractJsonString(mcmod, "version"),
                "mcmod.info"
            );
            FoundationSupport.requireVersionMatch(
                FoundationSupport.MOD_ID,
                FoundationSupport.extractJsonString(mcmod, "modid"),
                "mcmod.info mod ID"
            );
            Set<String> requiredMods = FoundationSupport.extractJsonStringArray(mcmod, "requiredMods");
            Set<String> expectedRequiredMods = Set.of("lwjgl3ify", "gtnhlib", "gtnhmixins");
            if (!requiredMods.equals(expectedRequiredMods)) {
                throw new IllegalStateException(
                    "mcmod.info requiredMods mismatch: expected=" + expectedRequiredMods + ", actual=" + requiredMods
                );
            }
            for (String optional : OPTIONAL_MOD_NAMES) {
                if (requiredMods.stream().anyMatch(value -> value.equalsIgnoreCase(optional))) {
                    throw new IllegalStateException("Optional integration is mandatory in mcmod.info: " + optional);
                }
            }

            Manifest manifest = inventory.manifest();
            requireManifest(manifest, "Lwjgl3ify-Aware", "true");
            requireManifest(manifest, "FMLCorePlugin", "com.seibel.distanthorizons.DistantHorizonsTweaker");
            requireManifest(manifest, "FMLCorePluginContainsFMLMod", "true");
            requireManifest(manifest, "FMLAT", "distanthorizons_at.cfg");
            requireManifest(manifest, "TweakClass", "org.spongepowered.asm.launch.MixinTweaker");
            requireManifest(manifest, "MixinConfigs", FoundationSupport.NORMAL_MIXIN_CONFIG);
            requireManifest(manifest, "Implementation-Version", expectedVersion);

            String sqlList = inventory.text("sqlScripts/scriptList.txt");
            List<String> migrations = sqlList.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
            if (migrations.isEmpty()) {
                throw new IllegalStateException("SQL migration list is empty");
            }
            for (String migration : migrations) {
                inventory.require("sqlScripts/" + migration);
            }

            List<String> forbiddenEntries = new ArrayList<>();
            for (String name : inventory.names()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (name.endsWith(".java")
                    || name.endsWith(".kt")
                    || name.endsWith(".gradle")
                    || name.endsWith(".gradle.kts")
                    || lower.contains(".gradle/")
                    || lower.contains("/build/")
                    || lower.startsWith("build/")
                    || lower.endsWith(".jar")) {
                    forbiddenEntries.add(name);
                }
                for (String prefix : FORBIDDEN_PACKAGES) {
                    if (name.startsWith(prefix)) {
                        forbiddenEntries.add(name);
                    }
                }
            }
            if (!forbiddenEntries.isEmpty()) {
                throw new IllegalStateException(
                    "Production artifact contains forbidden development, nested-mod, or external-mod entries: "
                        + forbiddenEntries.subList(0, Math.min(forbiddenEntries.size(), 25))
                );
            }

            int inspectedDhClasses = 0;
            for (String name : inventory.names()) {
                if (name.startsWith(FoundationSupport.ROOT_PACKAGE_PATH) && name.endsWith(".class")) {
                    int major = FoundationSupport.classMajor(inventory.bytes(name), name);
                    if (major != FoundationSupport.JAVA_21_CLASS_MAJOR) {
                        throw new IllegalStateException(
                            "Distant Horizons class has wrong class-file version: " + name
                                + " major=" + major + " expected=" + FoundationSupport.JAVA_21_CLASS_MAJOR
                        );
                    }
                    inspectedDhClasses++;
                }
            }
            if (inspectedDhClasses < 100) {
                throw new IllegalStateException(
                    "Production artifact contains too few Distant Horizons classes: " + inspectedDhClasses
                );
            }

            scanTextResourcesForLocalPaths(inventory);

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("artifactPath", artifact.getAbsoluteFile().toPath().normalize());
            report.put("artifactName", artifact.getName());
            report.put("artifactSize", Files.size(artifact.toPath()));
            report.put("artifactSha256", FoundationSupport.sha256(artifact.toPath()));
            report.put("classFileMajor", FoundationSupport.JAVA_21_CLASS_MAJOR);
            report.put("distantHorizonsClassCount", inspectedDhClasses);
            report.put("metadataVersion", expectedVersion);
            report.put("modInfoVersionConstant", expectedVersion);
            report.put("generatedVersionTokenClassPackaged", generatedTokenClassPackaged);
            report.put("mixinRefmap", FoundationSupport.REFMAP);
            report.put("mixinRefmapSha256", FoundationSupport.sha256(artifactRefmap));
            report.put("mixinMappingOwnerCount", mappedMixinOwners.size());
            report.put("sqlMigrationCount", migrations.size());
            report.put("sqliteNativeResourceCount", sqliteNatives.size());
            report.put("zstdNativeResourceCount", zstdNatives.size());
            report.put("productionIdentity", "reobfuscated-shadow");
            return report;
        }
    }


    private static Set<String> verifyProductionRefmap(String refmapText) {
        if (refmapText.trim().length() < 64
            || !refmapText.contains("\"mappings\"")
            || !refmapText.contains("\"data\"")
            || !refmapText.contains("\"searge\"")) {
            throw new IllegalStateException("Production Mixin refmap is structurally empty or incomplete");
        }

        Set<String> mappedMixinOwners = new java.util.LinkedHashSet<>();
        Matcher matcher = REFMAP_OWNER.matcher(refmapText);
        while (matcher.find()) {
            mappedMixinOwners.add(matcher.group(1));
        }

        if (mappedMixinOwners.size() < 5
            || !mappedMixinOwners.contains("com/seibel/distanthorizons/mixin/MixinMinecraft")
            || !mappedMixinOwners.contains("com/seibel/distanthorizons/mixin/MixinEntityRenderer")
            || !refmapText.contains("Lnet/minecraft/")
            || (!refmapText.contains("func_") && !refmapText.contains("field_"))) {
            throw new IllegalStateException(
                "Production Mixin refmap lacks the expected DH owner and obfuscated Minecraft mappings: "
                    + mappedMixinOwners
            );
        }

        return mappedMixinOwners;
    }

    public static void validateCandidateName(String name, String expectedVersion) {
        String expected = FoundationSupport.MOD_ID + "-" + FoundationSupport.validateVersion(expectedVersion) + ".jar";
        if (!expected.equals(name)) {
            throw new IllegalStateException(
                "Production artifact provider resolved the wrong JAR: expected=" + expected + ", actual=" + name
            );
        }
    }

    private static void requireManifest(Manifest manifest, String key, String expected) {
        String actual = manifest.getMainAttributes().getValue(key);
        if (!expected.equalsIgnoreCase(String.valueOf(actual))) {
            throw new IllegalStateException(
                "Manifest attribute mismatch for " + key + ": expected=" + expected + ", actual=" + actual
            );
        }
    }

    private static void scanTextResourcesForLocalPaths(FoundationSupport.ArchiveInventory inventory) throws IOException {
        for (String name : inventory.names()) {
            if (name.endsWith("/")) {
                continue;
            }
            String lowerName = name.toLowerCase(Locale.ROOT);
            boolean inspectable = lowerName.endsWith(".class")
                || lowerName.endsWith(".json")
                || lowerName.endsWith(".info")
                || lowerName.endsWith(".properties")
                || lowerName.endsWith(".cfg")
                || lowerName.endsWith(".txt")
                || lowerName.endsWith(".sql")
                || lowerName.endsWith(".xml")
                || lowerName.endsWith(".mf")
                || lowerName.endsWith(".toml")
                || lowerName.endsWith(".yml")
                || lowerName.endsWith(".yaml")
                || lowerName.endsWith(".gradle")
                || lowerName.endsWith(".gradle.kts")
                || lowerName.endsWith(".md");
            if (!inspectable) {
                continue;
            }

            byte[] bytes = inventory.bytes(name);
            if (bytes.length > 2 * 1024 * 1024) {
                continue;
            }
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            String lower = text.toLowerCase(Locale.ROOT);
            boolean windowsUserPath = false;
            for (char drive = 'a'; drive <= 'z'; drive++) {
                if (lower.contains(drive + ":\\users\\")) {
                    windowsUserPath = true;
                    break;
                }
            }
            if (text.contains("/Users/")
                || lower.contains("/home/")
                || windowsUserPath) {
                throw new IllegalStateException("Production artifact leaks a local filesystem path in " + name);
            }
        }
    }

}
