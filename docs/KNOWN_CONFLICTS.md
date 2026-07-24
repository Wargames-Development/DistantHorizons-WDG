# Known conflicts and audit rules

The compatibility audit opens JAR metadata where available and reports rather than modifying the profile.

Release-blocking conflicts include:

- OptiFine;
- FastCraft;
- BetterFPS;
- another Angelica, UniMixins, GTNHLib, lwjgl3ify or Distant Horizons JAR;
- standalone GTNHMixins;
- standalone MCPatcherForge;
- standalone NotFine;
- duplicate mod IDs or identical JAR bytes;
- duplicate LWJGL bootstrap mods;
- development, source or API JARs in the runtime profile.

GTNHMixins inside UniMixins and MCPatcherForge/NotFine inside Angelica are composite components, not separate external JAR duplicates. Their mod-list entries do not mean the user should add standalone JARs.

Multiple Mixin-capable composites are reported for review because ordering and version compatibility must be checked, but the audit does not blindly remove them.
