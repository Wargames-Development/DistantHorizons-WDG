# Upgrade from Change 006 or an existing Distant Horizons profile

1. Stop Minecraft normally and back up the profile.
2. Preserve the existing `config/Distant Horizons.toml`, world directories, Distant Horizons SQLite databases and LOD data.
3. Replace only the exact client-stack JARs listed by the release manifest. Remove any obsolete external lwjgl3ify runtime ZIP left by a pre-Change-005 package.
4. Remove old Distant Horizons, lwjgl3ify, GTNHLib, UniMixins or Angelica copies so only the manifest versions remain.
5. Do **not** copy the fresh-profile TOML over an existing config. Change 007 does not increment config version and defaults only affect newly generated profiles.
6. Start the profile, open the existing world, confirm the existing LOD database is reused, save, quit and reopen.

Custom render distance, quality preset, thread preset and generator mode remain user-controlled. A pre-existing updater setting may remain textually true, but WDG managed policy overrides its effective behaviour before any upstream service is contacted.
