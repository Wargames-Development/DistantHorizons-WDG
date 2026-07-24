package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

final class FixtureJars {

    static final String VERSION = "3.0.4-b-wdg-rc.1";
    static final String COMMIT = ProvenanceSupport.BASE_COMMIT;
    static final String DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private FixtureJars() {}

    static byte[] classBytes(int major) {
        return new byte[] {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe, 0, 0, (byte) (major >>> 8), (byte) major};
    }

    static byte[] classBytes(int major, String marker) {
        byte[] header = classBytes(major);
        byte[] markerBytes = marker.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[header.length + markerBytes.length];
        System.arraycopy(header, 0, combined, 0, header.length);
        System.arraycopy(markerBytes, 0, combined, header.length, markerBytes.length);
        return combined;
    }

    static Path createDistantHorizonsJar(Path jar, Set<String> omitted, int classMajor) throws IOException {
        return createDistantHorizonsJar(jar, omitted, classMajor, validDistantHorizonsRefmap());
    }

    static Path createDistantHorizonsJar(Path jar, Set<String> omitted, int classMajor, String refmap)
        throws IOException {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Lwjgl3ify-Aware", "true");
        attrs.putValue("FMLCorePlugin", "com.seibel.distanthorizons.DistantHorizonsTweaker");
        attrs.putValue("FMLCorePluginContainsFMLMod", "true");
        attrs.putValue("FMLAT", "distanthorizons_at.cfg");
        attrs.putValue("TweakClass", "org.spongepowered.asm.launch.MixinTweaker");
        attrs.putValue("MixinConfigs", FoundationSupport.NORMAL_MIXIN_CONFIG);
        attrs.putValue("Implementation-Version", VERSION);
        attrs.putValue("Distant-Horizons-Release-Channel", ProvenanceSupport.RELEASE_CHANNEL);
        attrs.putValue("Distant-Horizons-Updater-Policy", ProvenanceSupport.UPDATER_POLICY);
        attrs.putValue("Distant-Horizons-Git-Commit", COMMIT);
        attrs.putValue("Distant-Horizons-Tree-State", "modified");
        attrs.putValue("Distant-Horizons-Build-Source", ProvenanceSupport.VALIDATION_SOURCE);
        attrs.putValue("Distant-Horizons-Source-Digest", DIGEST);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mcmod.info", ("{\"modid\":\"distanthorizons\",\"version\":\"" + VERSION
            + "\",\"requiredMods\":[\"lwjgl3ify\",\"gtnhlib\",\"gtnhmixins\"]}").getBytes(StandardCharsets.UTF_8));
        entries.put("build_info.json", validBuildInfo().getBytes(StandardCharsets.UTF_8));
        entries.put(FoundationSupport.ACCESS_TRANSFORMER, "public net.minecraft.client.Minecraft field_71428_T".getBytes(StandardCharsets.UTF_8));
        String mixin = "{\"package\":\"com.seibel.distanthorizons.mixin\",\"refmap\": \"" + FoundationSupport.REFMAP + "\"}";
        entries.put(FoundationSupport.NORMAL_MIXIN_CONFIG, mixin.getBytes(StandardCharsets.UTF_8));
        entries.put(FoundationSupport.EARLY_MIXIN_CONFIG, mixin.getBytes(StandardCharsets.UTF_8));
        entries.put(FoundationSupport.REFMAP, refmap.getBytes(StandardCharsets.UTF_8));
        entries.put("sqlScripts/scriptList.txt", "0010.sql\n".getBytes(StandardCharsets.UTF_8));
        entries.put("sqlScripts/0010.sql", "CREATE TABLE fixture(id INTEGER);".getBytes(StandardCharsets.UTF_8));

        entries.put(
            "com/seibel/distanthorizons/coreapi/ModInfo.class",
            classBytes(classMajor, VERSION)
        );
        entries.put(
            "com/seibel/distanthorizons/coreapi/Tags.class",
            classBytes(classMajor, VERSION)
        );
        for (String name : new String[] {
            FoundationSupport.EARLY_LOADER,
            "com/seibel/distanthorizons/forge/ForgeMain.class",
            "com/seibel/distanthorizons/api/DhApi.class",
            "com/seibel/distanthorizons/mixin/MixinEntityRenderer.class",
            "com/seibel/distanthorizons/mixin/MixinMinecraft.class",
            "com/seibel/distanthorizons/core/config/Config$Client$Advanced$Debugging$ExampleConfigScreen$CategoryTest.class"
        }) {
            entries.put(name, classBytes(classMajor));
        }
        for (int i = 0; i < 100; i++) {
            entries.put("com/seibel/distanthorizons/fixture/Class" + i + ".class", classBytes(classMajor));
        }
        for (String name : new String[] {
            "com/electronwill/nightconfig/F.class",
            "net/jpountz/lz4/F.class",
            "net/jpountz/xxhash/F.class",
            "org/tukaani/xz/F.class",
            "org/sqlite/F.class",
            "org/sqlite/native/Linux/x86_64/libsqlitejdbc.so",
            "org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib",
            "org/sqlite/native/Windows/x86_64/sqlitejdbc.dll",
            "com/github/luben/zstd/F.class",
            "linux/amd64/libzstd-jni.so",
            "darwin/aarch64/libzstd-jni.dylib",
            "win/amd64/libzstd-jni.dll"
        }) {
            entries.put(name, new byte[] {1});
        }
        entries.put(
            "darwin/aarch64/libzstd-jni.dylib",
            "/home/native-builder/zstd/source.c".getBytes(StandardCharsets.UTF_8)
        );
        writeJar(jar, manifest, entries, omitted);
        return jar;
    }

    static String validBuildInfo() {
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"modId\": \"distanthorizons\",\n"
            + "  \"modVersion\": \"" + VERSION + "\",\n"
            + "  \"releaseChannel\": \"" + ProvenanceSupport.RELEASE_CHANNEL + "\",\n"
            + "  \"repository\": \"" + ProvenanceSupport.REPOSITORY + "\",\n"
            + "  \"upstreamRepository\": \"" + ProvenanceSupport.UPSTREAM_REPOSITORY + "\",\n"
            + "  \"branchOrChannel\": \"master\",\n"
            + "  \"commit\": \"" + COMMIT + "\",\n"
            + "  \"shortCommit\": \"" + COMMIT.substring(0, 12) + "\",\n"
            + "  \"treeState\": \"modified\",\n"
            + "  \"sourceTreeDigest\": \"" + DIGEST + "\",\n"
            + "  \"buildSource\": \"" + ProvenanceSupport.VALIDATION_SOURCE + "\",\n"
            + "  \"minecraftVersion\": \"1.7.10\",\n"
            + "  \"forgeVersion\": \"10.13.4.1614\",\n"
            + "  \"javaClassFileTarget\": 65,\n"
            + "  \"packageContractVersion\": \"" + ProvenanceSupport.PACKAGE_CONTRACT + "\",\n"
            + "  \"updaterPolicy\": \"" + ProvenanceSupport.UPDATER_POLICY + "\",\n"
            + "  \"reproducibleBuild\": true\n"
            + "}\n";
    }

    static String validDistantHorizonsRefmap() {
        return "{\"mappings\":{"
            + "\"com/seibel/distanthorizons/mixin/MixinMinecraft\":{\"timer\":\"field_71428_T:Lnet/minecraft/util/Timer;\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinEntityRenderer\":{\"renderWorld\":\"Lnet/minecraft/client/renderer/EntityRenderer;func_78471_a(FJ)V\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinChunk\":{\"fillChunk\":\"Lnet/minecraft/world/chunk/Chunk;func_76607_a([BIIZ)V\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinRenderGlobal\":{\"sortAndRender\":\"Lnet/minecraft/client/renderer/RenderGlobal;func_72719_a()V\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinTextureMap\":{\"loadTextureAtlas\":\"Lnet/minecraft/client/renderer/texture/TextureMap;func_110571_b()V\"}"
            + "},\"data\":{\"searge\":{"
            + "\"com/seibel/distanthorizons/mixin/MixinMinecraft\":{\"timer\":\"field_71428_T:Lnet/minecraft/util/Timer;\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinEntityRenderer\":{\"renderWorld\":\"Lnet/minecraft/client/renderer/EntityRenderer;func_78471_a(FJ)V\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinChunk\":{\"fillChunk\":\"Lnet/minecraft/world/chunk/Chunk;func_76607_a([BIIZ)V\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinRenderGlobal\":{\"sortAndRender\":\"Lnet/minecraft/client/renderer/RenderGlobal;func_72719_a()V\"},"
            + "\"com/seibel/distanthorizons/mixin/MixinTextureMap\":{\"loadTextureAtlas\":\"Lnet/minecraft/client/renderer/texture/TextureMap;func_110571_b()V\"}"
            + "}}}";
    }

    static Path createLwjgl3ifyJar(Path jar, Set<String> omitted) throws IOException {
        return createLwjgl3ifyJar(jar, omitted, Map.of(), false);
    }

    static Path createLwjgl3ifyJar(
        Path jar,
        Set<String> omitted,
        Map<String, byte[]> runtimeOverrides,
        boolean addUnexpectedEmbeddedRuntime
    ) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Implementation-Version", Lwjgl3ifyCompatibilityVerifier.EXPECTED_VERSION);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
            "mcmod.info",
            ("{\"modid\":\"lwjgl3ify\",\"version\":\""
                + Lwjgl3ifyCompatibilityVerifier.EXPECTED_VERSION + "\"}")
                .getBytes(StandardCharsets.UTF_8)
        );
        String[] classes = {
            "me/eigenraven/lwjgl3ify/relauncher/Lwjgl3ifyRelauncherTweaker.class",
            "me/eigenraven/lwjgl3ify/relauncher/Relauncher.class",
            "me/eigenraven/lwjgl3ify/relauncher/ChildProcessSupervisor.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/AutomaticRuntimeCoordinator.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/EmbeddedRuntimeArchiveProvider.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeInstaller.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeArchiveExtractor.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeManifest.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/JavaLaunchSelector.class"
        };
        for (String name : classes) {
            entries.put(name, classBytes(52));
        }
        entries.put(
            "me/eigenraven/lwjgl3ify/relauncher/runtime/java21-runtime-manifest.json",
            "{\"distribution\":\"Temurin\",\"javaRuntimeVersion\":\"21.0.11+10-LTS\",\"sha256\":\"fixture\"}"
                .getBytes(StandardCharsets.UTF_8)
        );
        entries.put("mixins.lwjgl3ify.json", "{\"refmap\":\"mixins.lwjgl3ify.refmap.json\"}".getBytes(StandardCharsets.UTF_8));
        entries.put(
            "mixins.lwjgl3ify.refmap.json",
            "{\"mappings\":{\"me/eigenraven/lwjgl3ify/mixins/early/game/MixinMinecraft_Display\":{\"x\":\"y\"}}}"
                .getBytes(StandardCharsets.UTF_8)
        );
        entries.put("META-INF/rfb-plugin/lwjgl3ify.properties", "pluginClass=fixture".getBytes(StandardCharsets.UTF_8));
        entries.put("me/eigenraven/lwjgl3ify/relauncher/forgePatches.zip", new byte[] {1, 2, 3});
        entries.put("me/eigenraven/lwjgl3ify/relauncher/version.json", "{\"change\":5}".getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> runtimeBytes = new LinkedHashMap<>();
        for (Lwjgl3ifyCompatibilityVerifier.RuntimeArchive runtime
            : Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES) {
            byte[] bytes = runtimeOverrides.getOrDefault(
                runtime.platformId(),
                ("fixture-runtime-" + runtime.platformId()).getBytes(StandardCharsets.UTF_8)
            );
            runtimeBytes.put(runtime.platformId(), bytes);
            entries.put(runtime.path(), bytes);
        }
        if (addUnexpectedEmbeddedRuntime) {
            entries.put(
                "me/eigenraven/lwjgl3ify/relauncher/runtime/embedded/runtimes/unexpected-primary.zip",
                "unexpected".getBytes(StandardCharsets.UTF_8)
            );
        }
        entries.put(
            Lwjgl3ifyCompatibilityVerifier.RUNTIME_DISTRIBUTION_MANIFEST,
            runtimeDistributionJson(runtimeBytes).getBytes(StandardCharsets.UTF_8)
        );
        writeJar(jar, manifest, entries, omitted);
        return jar;
    }

    private static String runtimeDistributionJson(Map<String, byte[]> runtimeBytes) {
        StringBuilder json = new StringBuilder("{\"schemaVersion\":1,")
            .append("\"artifactType\":\"lwjgl3ify-wdg-runtime-bundled\",")
            .append("\"distribution\":\"Temurin\",")
            .append("\"javaRuntimeVersion\":\"21.0.11+10-LTS\",")
            .append("\"embeddedPlatforms\":[");
        for (int index = 0; index < Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES.size(); index++) {
            Lwjgl3ifyCompatibilityVerifier.RuntimeArchive runtime =
                Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES.get(index);
            byte[] bytes = runtimeBytes.get(runtime.platformId());
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"").append(runtime.platformId())
                .append("\",\"sizeBytes\":").append(bytes.length)
                .append(",\"sha256\":\"").append(FoundationSupport.sha256(bytes))
                .append("\",\"packagedResource\":\"").append(runtime.path()).append("\"}");
        }
        json.append("],\"extensionPlatforms\":[");
        for (int index = 0; index < Lwjgl3ifyCompatibilityVerifier.OPTIONAL_EXTENSIONS.size(); index++) {
            Lwjgl3ifyCompatibilityVerifier.OptionalRuntimeExtension extension =
                Lwjgl3ifyCompatibilityVerifier.OPTIONAL_EXTENSIONS.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"").append(extension.platformId())
                .append("\",\"sizeBytes\":").append(extension.size())
                .append(",\"sha256\":\"").append(extension.sha256())
                .append("\",\"extensionFilename\":\"").append(extension.filename()).append("\"}");
        }
        return json.append("]}").toString();
    }

    static Path createGtnhLibJar(Path jar) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
            "mcmod.info",
            "[{\"modid\":\"gtnhlib\",\"version\":\"0.11.31\",\"mcversion\":\"1.7.10\"}]"
                .getBytes(StandardCharsets.UTF_8)
        );
        entries.put("com/gtnewhorizon/gtnhlib/GTNHLib.class", classBytes(52, "0.11.31"));
        entries.put("com/gtnewhorizon/gtnhlib/core/GTNHLibCore.class", classBytes(52));
        entries.put(
            "com/gtnewhorizon/gtnhlib/core/GTNHLibCoreModContainer.class",
            classBytes(52, "0.11.31")
        );
        entries.put("META-INF/versions/17/com/gtnewhorizon/gtnhlib/Java17.class", classBytes(61));
        entries.put("META-INF/gtnhlib_at.cfg", new byte[] {1});
        entries.put("mixins.gtnhlib.json", new byte[] {1});
        entries.put("mixins.gtnhlib.early.json", new byte[] {1});
        entries.put("META-INF/rfb-plugin/gtnhlib.properties", "version=0.11.31".getBytes(StandardCharsets.UTF_8));
        entries.put(RuntimeArtifactVerifier.GTNHLIB_NESTED_JAR, "fixture nested loader".getBytes(StandardCharsets.UTF_8));
        writeJar(jar, manifest, entries, Set.of());
        return jar;
    }

    static Path createUniMixinsJar(Path jar) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("FMLCorePlugin", "io.github.legacymoddingmc.unimixins.all.AllCore");
        manifest.getMainAttributes().putValue("TweakClass", "org.spongepowered.asm.launch.MixinTweaker");
        manifest.getMainAttributes().putValue("MixinConfigs", "mixins.gtnhmixins.json");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
            "mcmod.info",
            ("[{\"modid\":\"unimixins\",\"version\":\"0.1.23\",\"mcversion\":\"1.7.10\"},"
                + "{\"modid\":\"gtnhmixins\",\"version\":\"2.2.0+uni.0.1.23\",\"mcversion\":\"1.7.10\"}]")
                .getBytes(StandardCharsets.UTF_8)
        );
        entries.put("io/github/legacymoddingmc/unimixins/all/AllCore.class", classBytes(52));
        entries.put("com/gtnewhorizon/gtnhmixins/GTNHMixins.class", classBytes(52));
        entries.put("com/gtnewhorizon/gtnhmixins/core/GTNHMixinsCore.class", classBytes(52));
        entries.put("mixins.gtnhmixins.json", new byte[] {1});
        entries.put("mixins.gtnhmixins.refmap.json", new byte[] {1});
        writeJar(jar, manifest, entries, Set.of());
        return jar;
    }

    static Path createAngelicaJar(Path jar) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(
            "FMLCorePlugin",
            "com.gtnewhorizons.angelica.loading.AngelicaTweaker"
        );
        manifest.getMainAttributes().putValue("TweakClass", "org.spongepowered.asm.launch.MixinTweaker");
        manifest.getMainAttributes().putValue("MixinConfigs", "mixins.angelica.json");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
            "mcmod.info",
            "[{\"modid\":\"angelica\",\"version\":\"2.1.54\",\"mcversion\":\"1.7.10\"}]"
                .getBytes(StandardCharsets.UTF_8)
        );
        entries.put("com/gtnewhorizons/angelica/AngelicaMod.class", classBytes(52));
        entries.put("com/gtnewhorizons/angelica/loading/AngelicaTweaker.class", classBytes(52));
        entries.put("mixins.angelica.json", new byte[] {1});
        entries.put("META-INF/angelica_at.cfg", new byte[] {1});
        entries.put("META-INF/notfine_at.cfg", new byte[] {1});
        entries.put("META-INF/archaicfix_at.cfg", new byte[] {1});
        entries.put("META-INF/mcpatcherforge_at.cfg", new byte[] {1});
        entries.put("META-INF/versions/17/com/gtnewhorizons/angelica/Java17.class", classBytes(61));
        entries.put("META-INF/versions/21/com/gtnewhorizons/angelica/Java21.class", classBytes(65));
        writeJar(jar, manifest, entries, Set.of());
        return jar;
    }

    static void writeMetadata(Path pom, Path module, String optionalMarker) throws IOException {
        String extra = optionalMarker == null ? "" : "<dependency><groupId>x</groupId><artifactId>" + optionalMarker + "</artifactId><version>1</version></dependency>";
        String pomText = "<project><version>" + VERSION + "</version><dependencies>"
            + dep("com.github.GTNewHorizons", "lwjgl3ify", "3.0.28")
            + dep("com.github.GTNewHorizons", "GTNHLib", "0.11.31")
            + dep("com.github.GTNewHorizons", "UniMixins", "0.1")
            + extra + "</dependencies></project>";
        String moduleText = "{\"component\":{\"version\": \"" + VERSION + "\"},\"dependencies\":[\"lwjgl3ify\",\"gtnhlib\",\"unimixins\"]}";
        Files.writeString(pom, pomText, StandardCharsets.UTF_8);
        Files.writeString(module, moduleText, StandardCharsets.UTF_8);
    }

    private static String dep(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version></dependency>";
    }

    private static void writeJar(Path jar, Manifest manifest, Map<String, byte[]> entries, Set<String> omitted) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (omitted.contains(entry.getKey())) {
                    continue;
                }
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }
}
