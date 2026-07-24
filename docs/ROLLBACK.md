# Rollback

1. Stop Minecraft normally.
2. Back up the current profile, config, worlds and Distant Horizons databases.
3. Restore the previous exact client JAR set as one coherent stack. Restore an external runtime bundle only when rolling back to a historical split-runtime lwjgl3ify package that explicitly required it.
4. Do not mix Change 006 and Change 007 copies of Distant Horizons or lwjgl3ify.
5. Restore the previous config only when deliberately rolling back user settings; otherwise retain the compatible version-4 config.
6. Start a disposable copy first, open and reopen an existing world, then return to the normal profile.

Change 007 does not migrate or delete existing LOD data, reorder SQL migrations or alter storage paths. A rollback should therefore use the existing database backup rather than deleting data.
