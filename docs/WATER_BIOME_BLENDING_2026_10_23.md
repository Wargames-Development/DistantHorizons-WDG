# Legacy water biome blending

Minecraft 1.7.10 water callbacks previously saw a single supplied biome through FakeWorld. Correct warm/normal/cold water colours therefore met abruptly in the distant mesh.

The legacy client now applies a bounded, distance-weighted cross kernel to the topmost water segment in each LOD column, using the render source and four adjacent edge records already loaded for mesh building. No additional database queries, world access, network requests or generation are introduced. RGB alone changes; packed alpha, material, heights, lighting and texture palettes remain intact. The server protocol, persistent full data and world files are unchanged.

The existing LOD biome blending setting controls radius (0 disables, 1..3 sample columns; default 3). At coarse detail the radius scales with LOD column width, keeping distant transitions softer. This is a bounded approximation, not per-pixel interpolation: the current flat-colour mesh still has individual colour steps. Adjacent records contain one edge row, which is extended for the kernel rather than loading full neighboring sections. Missing or different-resolution neighbor data is skipped. Dry cells and differing water surface elevations stop each sampling ray. Submerged plant-coloured segments are left alone. Snapshotting the original center colours prevents traversal-order feedback.

Validation: seven focused regression tests cover uniform and disabled blending, gradual transitions, RGB-only mutation, neighboring sections, edge-only records, missing/different-detail neighbors, shorelines/different elevations and submerged plant isolation. Full core tests and Forge 1.7.10 check/assemble pass. In-game appearance has not yet been verified for this change.

Use the new client jar and reopen the world so meshes rebuild. Keep existing LOD databases. No server update or map reconversion is required by this client-only rendering change. Optional visual check: look across a warm/normal ocean boundary at LOD distance with biome blending set to 3 (already set in Release Testing).
