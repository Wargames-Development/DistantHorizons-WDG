# Fresh installation

This package is client-side. Do not copy the complete stack to a dedicated server.

1. Back up the profile and any existing Distant Horizons databases.
2. Start from a disposable Minecraft 1.7.10 Forge `10.13.4.1614` profile.
3. Copy the exact files from the release root `mods/` directory into the client `mods/` directory.
4. Do not create or copy a `lwjgl3ify/runtime/` directory. The exact Change 005 lwjgl3ify production JAR embeds the four primary Java 21 runtimes.
5. Keep the launcher on its normal Java 8 process. lwjgl3ify-wdg selects and relaunches the actual game using the embedded Temurin Java 21 runtime.
6. Linux AArch64 and Windows AArch64 systems require the matching optional manual extension asset; those extensions are not part of the default package.
7. Do not add standalone GTNHMixins, MCPatcherForge, NotFine, MixinExtras or UniMixins submodule JARs. They are supplied by composite artifacts.
8. Do not enable a shader pack during first validation. Angelica is included as rendering integration, but the package does not select a shader pack.
9. Start once, confirm the three-line RC warning, provenance log and `Updates are managed by the WDG modpack` message, then create a disposable world.

The fresh profile defaults are LOW quality, MINIMAL_IMPACT threading, 128-chunk LOD radius, SURFACE generation, distant generation enabled and automatic/silent updater disabled.
