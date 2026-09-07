# Combined client diagnostics and release assets

Change 007 keeps three deterministic diagnostic stages:

- Stage A: the runtime-bearing lwjgl3ify-wdg Change 005 JAR, GTNHLib, and UniMixins/GTNHMixins.
- Stage B: Stage A plus Distant Horizons WDG `3.0.4-b-wdg-rc.1`.
- Stage C: Stage B plus Angelica `2.1.54`.

Each validation package records `UNCOMMITTED_VALIDATION`, Change 007 base commit `170c809b2befbb3c54bd143ac68f8a9329e41f07`, modified tree state and source-tree digest. Final packages instead require the exact clean Change 007 commit.

The separate release-candidate root is `DistantHorizons-WDG-3.0.4-b-wdg-rc.1/` and contains the exact mod JARs, manifest, dependency lists, checksums, and deployment documents. The one lwjgl3ify JAR embeds the primary Linux x86_64, macOS AArch64, macOS x86_64, and Windows x86_64 Temurin archives. No `lwjgl3ify/runtime/` directory is created. Linux AArch64 and Windows AArch64 extensions remain optional manual assets. Generated packages remain under `build/` and are never tracked.
