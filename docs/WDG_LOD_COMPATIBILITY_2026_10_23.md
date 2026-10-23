# WDG DH compatibility pass — 23 October 2026

## Generation limits

The Forge 1.7.10 server snapshots the optional Bukkit WorldBorder plugin on the server thread, at level load and every 20 ticks. It maps the actual native WorldServer handle to its CraftWorld, so an unrelated world's border is never borrowed. Rectangular borders, including globe topology, provide limits; absent plugins, absent per-world borders, round borders and unavailable reflection retain ordinary DH behaviour. No hardcoded Earth bounds are used.

Worker threads read immutable snapshots. Both new and queued generation requests are checked. Edge sections split using DH's existing split result. Wholly outside requests are rejected before terrain generation or database retrieval. Client and server enforcement are independent. Clients receive current bounds, updates and border-removal notices; outside render-tree requests are not submitted. Existing chunk-update events outside the configured rectangle are not captured as new LOD terrain.

The built-in 1.7 generator also excludes outside neighbour chunks. Per-event acquisition records keep reference counts and ticket release balanced if the border changes, a request fails, or cleanup overtakes a queued request. This bounds DH's own explicit chunk requests; it is not a global prohibition on unrelated mods' world generation or player-triggered chunk loads.

DH full-data leaves are 64x64 blocks. Borders not aligned to these leaves may leave a narrow unrequested edge strip because an intersecting minimum-size leaf is rejected rather than generating outside. The Earth map's actual faces X ±20992 and Z ±12288 align exactly, so it does not have this limitation. Already stored outside data is not deleted or trimmed. Aggregation still uses DH's normal parent hierarchy.

**Install this build on both DH clients and the DH server. Protocol 17 intentionally rejects old protocol-16 peers instead of silently changing an existing packet's layout.** Normal Minecraft connectivity does not depend on DH protocol support.

## Multiplayer storage

A normalized endpoint (host plus port; default 25565) chooses the root regardless of server-list display label or connection menu. Host case, trailing DNS dots and explicit/implicit default ports normalize. IPv6 brackets and nondefault ports are supported. Server-provided level keys still separate dimensions/world identities; server-provided root keys no longer override the endpoint. Replays retain their separate root. Custom connections with no ServerData use the actual network endpoint, and missing endpoint identity is an error instead of writing to a shared NA folder.

DNS aliases and an explicitly entered numeric IP are not automatically equated. No DNS lookup is performed to guess that different endpoints represent the same server. A menu that bypasses ServerData may expose only its resolved network endpoint; use the same address consistently or migrate an identified old dataset explicitly.

Before opening a database, one identifiable old keyed/IP-labelled root is moved using a no-replace filesystem operation. Existing destination roots are retained. Multiple candidates, name-only datasets without endpoint evidence, and symlink candidates are preserved without merging or deleting data. Ambiguous datasets require a deliberate choice; this pass does not silently choose the largest/newest database. External save-structure API overrides remain authoritative.

## EFR rendering

The existing contained-water metadata contracts remain the basis of aquatic classification. Kelp, seagrass, tall seagrass, wet sea pickles and wet coral retain water material/alpha. Submerged cells can approximate their plant/coral texture colour when covered by water; the exposed water surface is not tinted with plant colour. Dry states and opaque coral blocks are not interpreted as water. Dripleaf wet states retain their existing masks.

Powder snow is explicitly retained as a full visible LOD volume. This changes only DH's render classification, not Minecraft collision or gameplay.

1.7 tint lookup invokes the block's own colour callback using a thread-local biome snapshot. BlockBush and IShearable inheritance no longer decide whether to tint. This preserves fixed spruce/birch, white/non-tinted flowers and custom mod callbacks instead of forcing a generic grass tint. Water callbacks use the stored LOD biome; EFR's existing Forge water-colour event supplies imported biome profiles. The fake access no longer claims empty neighbouring cells are non-air. Existing protection against skipped foliage colouring wood/leaves or tall terrain columns was already present and remains in place.

The CSV audits every EFR registry entry in the current template and states its policy. It does **not** certify the visual appearance of every metadata state. DH renders simplified columns/cubes: this pass does not add arbitrary full-resolution plant meshes, tile-entity models or per-block animation at LOD distances. Resource packs and custom-render-only blocks without meaningful icons may still need individual adapters. Existing LOD geometry caches may need rebuilding in a disposable dataset to evaluate changed colours/solidity; no existing databases were removed.

## Updates and zoom

All public self-update entry points refuse work unconditionally in WDG builds, regardless of saved user settings. Upstream Modrinth metadata initialization and GitLab constructor/query paths also return without network work. Updater defaults are false. No WDG updater provider is introduced.

Projection-derived zoom remains the source of truth; no speculative Angelica FOV override was added. Set JVM option `-Dwdg.dh.zoomDiagnostics=true` for throttled projection-scale/configured-FOV/raw-magnification logs while testing normal view versus zoom. Invalid/nonfinite magnification safely resets. An Angelica graphics-runtime test remains necessary to establish whether a further adapter is needed.

## Validation and minimum runtime check

The complete core suite and Forge 1.7.10 check/assemble are run for this pass. Focused tests cover edge/corner footprints, splitting, no-border fallback, protocol round-trip/removal, endpoint normalization, no-overwrite/ambiguous storage migration, disabled updater metadata and reflection against multiple worlds/absent plugins. Test-only Bukkit fixtures are never included in release JARs.

Automated checks cannot certify graphics output or the live Crucible plugin classloader. Before production use, perform one short test on a disposable copy:

1. Install the same new DH jar on client and Crucible. Confirm a rectangular globe border is reported and watch chunk-file counts near one edge while DH generation is enabled; the DH pass must not create outside chunks. Remove the test border and confirm ordinary requests resume. Do not remove your production border for this test.
2. View a powdered-snow patch and ocean/river/swamp water with kelp/seagrass beyond vanilla distance. Compare foliage colours and confirm nearby logs retain their own colour. Use a fresh disposable LOD dataset if cached geometry predates this patch.
3. Connect to that same address using the server list and Direct Connect/custom menu; the log must use the same endpoint root and level key. Do not delete old datasets to test this.
4. If testing zoom, enable the diagnostic JVM flag and compare normal versus zoom magnification with Angelica. Disable the flag afterward.

No existing world, pack JAR, LOD database or plugin configuration is modified by building this pass.

Final automated result: **78 core tests across 18 suites, zero failures**, original standalone aquatic metadata contract passed, Forge check/assemble passed. Owned release classes target Java 8 (major 52); test Bukkit/WorldBorder fixtures are absent from the release jar.
