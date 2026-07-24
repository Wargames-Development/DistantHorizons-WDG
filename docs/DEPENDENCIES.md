# Exact release-candidate dependencies

Required client artifacts:

- `distanthorizons-3.0.4-b-wdg-rc.1.jar`;
- the verified lwjgl3ify-wdg Change 005 runtime-bearing production JAR;
- `gtnhlib-0.11.31.jar`;
- `+unimixins-all-1.7.10-0.1.23.jar`, which supplies GTNHMixins;
The verified lwjgl3ify Change 005 JAR is itself the runtime distribution. It embeds exactly four primary Temurin Java 21 archives: Linux x86_64, macOS AArch64, macOS x86_64, and Windows x86_64. Linux AArch64 and Windows AArch64 are optional manual extensions and are not required package inputs.

Included rendering integration:

- `angelica-2.1.54.jar`.

Do not add GTNHMixins, MCPatcherForge, NotFine, UniMixins submodules, MixinExtras, or other Angelica/UniMixins internal components as separate JARs. Composite component entries may appear in the Forge Mods list and are expected.
