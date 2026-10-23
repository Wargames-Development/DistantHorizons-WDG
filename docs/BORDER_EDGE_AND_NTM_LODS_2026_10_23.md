# Boundary chunks and NTM structural LODs

## Border correction

Previously, partial 64-block leaves and every 16-block chunk crossing a rectangular WorldBorder were rejected. This lost valid inside terrain whenever a border was not aligned to DH sections/chunks.

Intersecting requests still split above the minimum 64-block leaf. A partial leaf can now complete using intersecting chunks only. The built-in legacy generator filters both target chunks and explicitly loaded neighbors; wholly outside chunks are not requested by this path. Chunk capture accepts straddling chunks too. Since Minecraft loads/generates whole chunks, the outside portion of each boundary chunk necessarily exists; a partial chunk cannot be generated independently. Vanilla/mod population can perform its own neighbor loads, which DH's request filtering does not universally intercept.

Client mesh quads are clipped to the received border before merging, including coarse cells, so existing cached outside terrain does not extend rendered LOD geometry beyond it. Border coordinates use whole-block faces; fractional limits round inward by less than one block. Clipping does not manufacture vertical end caps or recover detail already lost through coarse aggregation. The client uses the DH level's received bounds, not just the wrapper's default null bounds.

Generation completion and missing-column inspection ignore columns in wholly outside chunks. This is necessary for partial leaves to finish multiplayer generation requests and pregen instead of repeatedly requesting excluded columns. The existing strict containsChunk predicate remains available; generation/capture explicitly use intersectsChunk.

No configured rectangular border means existing behavior. No wire format, database schema, WorldBorder plugin or HBM changes. Install the updated DH jar on both server and client: older clients/servers retain the former request rejection behavior. Restart/reconnect to clear old requests and rebuild meshes. Retain LOD databases; no map reconversion required.

## NTM approximation

DH samples ordinary block icons, while many NTM machines render their visible surfaces with TESRs/custom model renderers. The generic missing-texture color path returns invisible; this can remove the resulting LOD even though the block occupies a structural volume. Sparse scaffold/fence texture samples can also give weak transparency.

For hbm blocks made of native iron/rock/wood/anvil materials, the legacy color cache now tries all six ordinary face icons when the initial icon is unusable. It retains a usable native icon color, or falls back to the native material's map color. Structural samples are opaque LOD approximations; plants, leaves, glass, fluids, other mod namespaces and actual block collision remain unchanged. TESR-only structural blocks use the representative flat color rather than attempting to bake nonexistent terrain face textures. Known invisible logic blocks, jigsaw markers and zero-height dummy plates are excluded from LOD conversion. Dummy ports with actual volume are retained.

This represents occupied block cells, not full NTM machine geometry, animation, connected textures or tile-entity state. Models extending beyond their occupied cells cannot be reconstructed by this pass. In-game appearance across all NTM blocks has not been exhaustively validated.

## Validation

99 core tests pass (including 10 new border/NTM regression tests); Forge 1.7.10 check/assemble passes. New tests cover positive and negative nonaligned edges, partial leaf acceptance and large-section splitting, outside rejection, chunk filtering/completion, top/bottom/side clipping, metadata/color/light retention, unchanged unconfigured behavior, scoped NTM fallback and helper exclusion. Source inspection traced capture, request queues, server requests, generator targets/neighbors, completion and client mesh paths.

Minimal in-game check: after updating both endpoints, revisit one straight border and one corner, then one NTM factory/scaffold or machine area from near and LOD distance. Ensure edge terrain fills to the border and wholly outside LOD faces do not show. Check server logs once for repeated requests at the same edge section. The new jar is built but not installed automatically.
