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

    static final String VERSION = "3.0.4-b-dev";

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

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mcmod.info", ("{\"modid\":\"distanthorizons\",\"version\":\"" + VERSION
            + "\",\"requiredMods\":[\"lwjgl3ify\",\"gtnhlib\",\"gtnhmixins\"]}").getBytes(StandardCharsets.UTF_8));
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
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Implementation-Version", "3.0.28-4-g7500f19");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mcmod.info", "{\"modid\":\"lwjgl3ify\",\"version\":\"3.0.28-4-g7500f19\"}".getBytes(StandardCharsets.UTF_8));
        String[] classes = {
            "me/eigenraven/lwjgl3ify/relauncher/Lwjgl3ifyRelauncherTweaker.class",
            "me/eigenraven/lwjgl3ify/relauncher/Relauncher.class",
            "me/eigenraven/lwjgl3ify/relauncher/ChildProcessSupervisor.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/AutomaticRuntimeCoordinator.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeInstaller.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeArchiveExtractor.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/RuntimeManifest.class",
            "me/eigenraven/lwjgl3ify/relauncher/runtime/JavaLaunchSelector.class"
        };
        for (String name : classes) {
            entries.put(name, classBytes(52));
        }
        entries.put("me/eigenraven/lwjgl3ify/relauncher/runtime/java21-runtime-manifest.json", "{\"distribution\":\"Temurin\",\"sha256\":\"fixture\"}".getBytes(StandardCharsets.UTF_8));
        entries.put("mixins.lwjgl3ify.json", "{\"refmap\":\"mixins.lwjgl3ify.refmap.json\"}".getBytes(StandardCharsets.UTF_8));
        entries.put(
            "mixins.lwjgl3ify.refmap.json",
            "{\"mappings\":{\"me/eigenraven/lwjgl3ify/mixins/early/game/MixinMinecraft_Display\":{\"x\":\"y\"}}}"
                .getBytes(StandardCharsets.UTF_8)
        );
        entries.put("META-INF/rfb-plugin/lwjgl3ify.properties", "pluginClass=fixture".getBytes(StandardCharsets.UTF_8));
        entries.put("me/eigenraven/lwjgl3ify/relauncher/forgePatches.zip", new byte[] {1, 2, 3});
        entries.put("me/eigenraven/lwjgl3ify/relauncher/version.json", "{\"change\":4}".getBytes(StandardCharsets.UTF_8));
        writeJar(jar, manifest, entries, omitted);
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
