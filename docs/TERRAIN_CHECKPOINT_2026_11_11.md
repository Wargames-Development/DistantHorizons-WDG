# Terrain checkpoint — 11 November 2026

This commit separates the terrain work from the still-unresolved actor shader/zoom investigation.

Changes: match retained render materials to source textures; preserve wood/leaf boundaries during full-data occlusion and preferred vertical reduction; keep EFR paths/farmland classified as dirt with correctly referenced side textures; guard SSAO sampling/blur at viewport edges, sky boundaries and forward/reverse physical depth. The GL and Blaze shader/uniform layouts are updated together. Tests cover material selection, tree occlusion/reduction and path colour. Native CGL checks exercise the actual GL AO shaders.

The user confirmed the tree/terrain issue was fixed after X66 and previously confirmed EFR path-side colour. Exact ChickenChunks block/TESR shape parity remains open. Very coarse LOD budgets still simplify geometry, and old lossy cached data cannot recreate discarded geometry without refreshing. No terrain database or packet format is changed and no chunk loading is added.

Validation immediately before commit: core suite PASS (126 tests / 30 suites); standalone SSAO CGL fixture PASS (54 GPU checks). Existing actor fixture also PASS (108 checks), but that is not runtime proof of actor shader correctness. Forge 1.7.10 compilation passes; modern Blaze game runtime was not executed. `git diff --check` passes.

The companion rendering checkpoint and handoff document explicitly retain OPEN Angelica-zoom laser failures and shader vehicle jitter. This terrain commit must not be presented as resolving those defects.
