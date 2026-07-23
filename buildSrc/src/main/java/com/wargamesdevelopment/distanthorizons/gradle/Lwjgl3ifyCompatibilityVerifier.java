package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Manifest;

public final class Lwjgl3ifyCompatibilityVerifier {

    private static final String RUNTIME_MANIFEST =
        "me/eigenraven/lwjgl3ify/relauncher/runtime/java21-runtime-manifest.json";
    private static final String REFMAP = "mixins.lwjgl3ify.refmap.json";
    private static final String MIXIN_CONFIG = "mixins.lwjgl3ify.json";
    private static final List<String> REQUIRED_MEMBERS = List.of(
        "mcmod.info",
        "me/eigenraven/lwjgl3ify/relauncher/Lwjgl3ifyRelauncherTweaker.class",
        "me/eigenraven/lwjgl3ify/relauncher/Relauncher.class",
        "me/eigenraven/lwjgl3ify/relauncher/ChildProcessSupervisor.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/AutomaticRuntimeCoordinator.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeInstaller.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeArchiveExtractor.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeManifest.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/JavaLaunchSelector.class",
        RUNTIME_MANIFEST,
        MIXIN_CONFIG,
        REFMAP,
        "META-INF/rfb-plugin/lwjgl3ify.properties",
        "me/eigenraven/lwjgl3ify/relauncher/forgePatches.zip",
        "me/eigenraven/lwjgl3ify/relauncher/version.json",
        "META-INF/MANIFEST.MF"
    );
    private static final List<String> JAVA_8_BOUNDARY_CLASSES = List.of(
        "me/eigenraven/lwjgl3ify/relauncher/Lwjgl3ifyRelauncherTweaker.class",
        "me/eigenraven/lwjgl3ify/relauncher/Relauncher.class",
        "me/eigenraven/lwjgl3ify/relauncher/ChildProcessSupervisor.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/AutomaticRuntimeCoordinator.class",
        "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeInstaller.class"
    );

    private Lwjgl3ifyCompatibilityVerifier() {}

    public static Map<String, Object> verify(File artifact) throws IOException {
        FoundationSupport.requireReadableRegularFile(artifact, "Production lwjgl3ify-wdg JAR");
        validateCandidateName(artifact.getName());
        try (FoundationSupport.ArchiveInventory inventory = FoundationSupport.ArchiveInventory.open(artifact)) {
            for (String required : REQUIRED_MEMBERS) {
                inventory.require(required);
            }
            String mcmod = inventory.text("mcmod.info");
            String modId = FoundationSupport.extractJsonString(mcmod, "modid");
            if (!"lwjgl3ify".equals(modId)) {
                throw new IllegalStateException("lwjgl3ify public mod identity changed: " + modId);
            }
            String version = FoundationSupport.extractJsonString(mcmod, "version");
            FoundationSupport.validateVersion(version);

            Manifest manifest = inventory.manifest();
            String implementationVersion = manifest.getMainAttributes().getValue("Implementation-Version");
            if (implementationVersion == null || implementationVersion.isBlank()) {
                implementationVersion = version;
            }

            String mixinConfig = inventory.text(MIXIN_CONFIG);
            if (!mixinConfig.contains(REFMAP)) {
                throw new IllegalStateException("lwjgl3ify production Mixin config does not reference " + REFMAP);
            }
            String refmap = inventory.text(REFMAP);
            String displayMixinOwner = "me.eigenraven.lwjgl3ify.mixins.early.game.MixinMinecraft_Display";
            String slashDisplayMixinOwner = displayMixinOwner.replace('.', '/');
            if (refmap.trim().length() < 32
                || !refmap.contains("\"mappings\"")
                || (!refmap.contains(displayMixinOwner) && !refmap.contains(slashDisplayMixinOwner))) {
                throw new IllegalStateException("lwjgl3ify production refmap is structurally invalid");
            }
            String runtimeManifest = inventory.text(RUNTIME_MANIFEST).toLowerCase(Locale.ROOT);
            if (!runtimeManifest.contains("temurin") || !runtimeManifest.contains("sha256")) {
                throw new IllegalStateException("Canonical Java runtime manifest is structurally invalid");
            }

            List<String> forbidden = new ArrayList<>();
            for (String name : inventory.names()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".java")
                    || lower.contains("java21-runtimes.zip")
                    || lower.contains("lwjgl3ify-wdg-java21-runtimes")
                    || lower.contains("openjdk21u-jre")
                    || lower.startsWith("runtimes/")) {
                    forbidden.add(name);
                }
            }
            if (!forbidden.isEmpty()) {
                throw new IllegalStateException("Production lwjgl3ify JAR contains forbidden runtime/source payloads: " + forbidden);
            }

            int maxEarlyMajor = 0;
            for (String name : JAVA_8_BOUNDARY_CLASSES) {
                int major = FoundationSupport.classMajor(inventory.bytes(name), name);
                if (major > 52) {
                    throw new IllegalStateException(
                        "Early lwjgl3ify relaunch class is not Java 8 compatible: " + name + " major=" + major
                    );
                }
                maxEarlyMajor = Math.max(maxEarlyMajor, major);
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("artifactPath", artifact.getAbsoluteFile().toPath().normalize());
            report.put("artifactName", artifact.getName());
            report.put("artifactSize", Files.size(artifact.toPath()));
            report.put("artifactSha256", FoundationSupport.sha256(artifact.toPath()));
            report.put("implementationVersion", implementationVersion);
            report.put("publicModId", modId);
            report.put("mixinRefmap", REFMAP);
            report.put("mixinRefmapSha256", FoundationSupport.sha256(inventory.bytes(REFMAP)));
            report.put("earlyRelaunchClassMajorMax", maxEarlyMajor);
            report.put("productionIdentity", "change-004-compatible-automatic-packaged-java");
            report.put("compatible", true);
            return report;
        }
    }

    public static void validateCandidateName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar")) {
            throw new IllegalStateException("lwjgl3ify production input is not a JAR: " + name);
        }
        for (String rejected : List.of("-dev.jar", "-dev-preshadow.jar", "-sources.jar", "-api.jar")) {
            if (lower.endsWith(rejected)) {
                throw new IllegalStateException("Development or sources lwjgl3ify artifact rejected as production: " + name);
            }
        }
    }
}
