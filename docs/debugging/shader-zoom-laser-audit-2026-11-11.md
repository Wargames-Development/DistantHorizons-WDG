# Shader, Angelica zoom and MCHELI laser audit — 11 November 2026

Historical diagnosis before implementation. See the subsequent [X70/X206/X10 correction candidate](shader-zoom-laser-fix-2026-11-11.md). During this diagnosis, production code, installed mods, shader settings, worlds and databases were not changed. No client launch, weapon firing, deployment or visual acceptance was performed.

## Outcome

The current implementation is not accepted for stationary shader actor stability, Angelica zoom designation, or complete Et Futurum laser compatibility. There are two independently evidenced failure paths: shader actor integration and unavailable/unsupported laser terrain. The available log cannot attribute every zoom dropout to one of them.

Do not claim that shader packs fundamentally cannot support LOD models. The new path introduces camera, depth and temporal contracts which the older MCHELI presentation did not have to satisfy in the same way.

## Evidence and limits

Inspected DH checkpoint `83ab4498c`, MCHELI checkpoint `b0ab8cc`, local EFR source, the integration-test instance's `fml-client-latest.log`, installed Angelica 2.2.14 bytecode, and the installed BountifulStars water-input-probe shader archive and options sidecar. The log covers the 02:xx session on 11 November. It does not contain per-frame screenshots, matrices, zoom state or designation confirmation transitions.

The log records shader jitter strength **0.0** for program 109 at 02:18:03 and program 159 at 02:25:07. It also records 20 laser UNKNOWN diagnostics between 02:14:27 and 02:14:55: 12 initial-pending snapshots, six unsupported-shape failures and two expired snapshots. These laser entries precede the two jitter initialisation messages; do not describe them as proof that shaders or zoom caused those queries to fail. Logging stops after 20 reports for that cache/world, so the absence of later messages does not prove later queries succeeded.

The pack defaults to TAA_MODE=1 and TAA_JITTER=1; no TAA override was found in its options sidecar. Its vertex shader explicitly applies TAAJitter to DH clip XY. Runtime compiled options and actual per-frame offset still need capture.

## B1 — The X68 jitter correction was inactive in the observed shader sessions

`ShaderActorJitter.java:20–49` discovers strength from attached vertex source and returns immediately for strength zero. `ActorRenderer.java:212–216` uses that output for both actor/native and DH projection. Both recorded initialisations therefore supplied zero displacement.

This is a confirmed observation about the correction, not proof of why detection returned zero. The logger conflates disabled jitter, unmatched source, source not inspected, and unsupported pattern. It records neither attached shader count nor matched table/source/option status. A zero result is cached until the pipeline/program changes.

A read-only offline probe preprocessed the installed jitter include for mode 1 and passed it through the installed Angelica GLSL parser/printer. It retained the eight offsets, `offset *= 0.125`, and `return coord + offset`. Basic printer whitespace alone did not reproduce the failure. The full DH transformer probe could not run without its LWJGL runtime dependency; no full transformed-program result is claimed.

Important rejected assumption: the generic Iris ProgramCreator detaches shaders, but IrisLodRenderProgram uses its own attach/link path. Its shader destroy calls do not by themselves prove detachment or unavailable source. Inspect the actual DH program rather than importing a conclusion from the generic builder.

Missing jitter alignment can move depth sampling relative to foreground edges even with a stationary camera and actor. Whether this accounts for the size and appearance of the reported model motion remains unmeasured.

## B2 — Actor jitter and temporal handling need one coordinated policy

`actor_model.frag:107` writes material mask 254. The installed pack's `taa.glsl:151–155` returns without temporal accumulation for that mask. Meanwhile ActorRenderer deliberately shifts actor rasterisation by the pack jitter whenever a nonzero offset is detected.

Consequently, making B1 return a nonzero offset is not sufficient evidence of a stable final actor image: the actor would receive a changing raster offset while its pixels bypass the temporal reconstruction normally paired with that offset. This is a concrete policy interaction; its final visual contribution requires measured frames. Removing mask 254 blindly is also unsafe, because it intentionally avoids other inherited terrain processing, including SSAO. Colour history, material handling and depth sampling must be validated together.

At mode 1, clip displacement is only one eighth of the listed offsets divided by view size. Do not attribute large world-space jumps to this small subpixel offset without measuring their screen-space amplitude.

## B3 — DH laser collision support is materially narrower than native collision

MCHELI `WGMapLaserClientBridge.traceDetailed` uses native block collision for loaded chunks, then calls DhLaserTerrainBridge for missing terrain whenever DH is present. It does not fall back to WGMap when an installed DH returns UNKNOWN. This preserves conservative obstruction handling but changes availability relative to the older integration.

DH `DhTerrainRayApi.kind` recognises vanilla ignored plants/liquids, EFR dirt paths, farmland, BlockSlab subclasses, BlockStairs subclasses, vanilla snow layers, selected vanilla glass/ice/leaves, and a generic opaque/normal-render fallback. Unsupported types return UNKNOWN. This is not a complete mod block collision contract.

| EFR family | Current DH fallback | Consequence / verification needed |
| --- | --- | --- |
| Ordinary opaque cubes | Generic opaque/normal classification | Supported only insofar as native laser shape is actually a full cube; render predicates do not prove that for every subclass. |
| Grass/dirt paths | Explicit 15/16-height support | Compare top and side hits with native trace. |
| Vanilla-derived slabs/stairs | Metadata-based support | Check EFR subclasses and every orientation/half against native ray tracing. |
| Azalea leaves and other EFR leaves | No BlockLeaves-family rule; only vanilla leaf identities are explicit | Azalea leaf states appear in unsupported snapshot candidates. Test fancy/fast graphics and client/server consistency. |
| Mangrove roots | Not opaque and not normal rendering; no explicit rule | Source inherits a cube block shape but custom render flags make DH reject it. Registry candidate is present in the log. |
| Short/tall dry grass and other EFR vegetation | Not in vanilla identity-based ignore policy | Dry grasses appear in unsupported candidates. Match the intended soft-plant policy on both loaded and stored terrain. |
| Amethyst buds/clusters | No oriented partial-box encoding | Source sets size/orientation-dependent bounds; many candidate states occur in the log. Treating these as air or full cubes would both be wrong. |
| Doors, trapdoors, fences, gates, walls, panes and connections | No general state/neighbour-aware shape support | Need exact metadata/neighbour inputs or explicit UNKNOWN when those inputs are absent. |
| Powder snow and other special blocks | No explicit ray policy | EFR distinguishes entity movement collision from ray selection. Match native laser ray semantics, not visual solidity or player collision boxes. |
| Waterlogged/aquatic/decorative blocks | No complete shared classification | Check water-ignore behaviour without discarding solid contents or attachments. |

The unsupported-name list contains at most 16 candidate states from converted snapshots. It is not the exact state at each failing cell. It proves coverage gaps, not that every listed block caused a particular reported dropout.

MCHELI `MCH_LaserGuidanceSystem.java:135–141` sets targetConfirmed from the trusted trace; UNKNOWN clears confirmation and can send an inactive designation. `MCH_RenderLockBox.java:45–46` then suppresses the green HUD. Thus some missing markers are real lost designation, independently of rendering.

## B4 — Snapshot refresh has a measurable availability constraint

DhTerrainRayApi permits one snapshot adoption per 50 ms, refreshes after one second and invalidates after two seconds. The cache holds 32 sections. At the full cache size, refreshing all entries takes at least 1.6 seconds at that adoption limit, even before I/O latency. This does not prove every single ray will fail: a ray can touch fewer sections. For a synchronised cohort first due for refresh at age one second, the last adoption can occur after the two-second deadline. Staggered entries can behave better; this arithmetic is not proof of sustained cache failure. Cache size alone is not a guarantee of usable data.

The log reached 32 cached sections and contains actual expired-snapshot failures. Queries return on their first unknown cell, making later corridor preparation dependent on subsequent queries. This is an availability/performance issue to measure separately from zoom FOV. Do not fix it by accepting an unverified surface beyond unknown terrain or increasing budgets without profiling.

## T1 — Exact zoom-specific cause is still open

Installed Angelica's zoom mixin divides the renderer FOV by current zoom and adjusts sensitivity. It does not directly modify the MCHELI world-space laser ray. Therefore a held-centre designation should not require a different world ray merely because FOV narrows.

The current actor pipeline reads its native matrix from DH's earlier ActiveRenderInfo snapshot, reads the shader DH projection through Angelica RenderingState, then composites before shader finalisation. The green designation HUD separately replays matrices captured at RenderWorldLast into overlay rendering. Agreement between these views is required; existing logs do not establish it during zoom transitions/private cameras.

The client ray starts at renderViewEntity.posXYZ, while the server reconstructs a player-eye or UAV-aircraft origin. These quantities cannot be assumed equivalent for every camera/seat implementation. Check the actual entity type, effective model-view eye and source position during reproduction before changing eye heights. Zoom magnifies any existing angular/parallax discrepancy; this is a candidate, not a confirmed diagnosis.

## Why the legacy MCHELI path can appear stable

The retained MCH_RenderFarVehicle path uses RenderWorldLast, preserves current projection XY, scales position/model together, and explicitly selects fixed-function model lighting. It uses its own WGMap/native depth integration and optional precision layer. It is not generally free of offscreen rendering.

The DH-owned path instead captures actors under an isolated projection, reconstructs world position from model depth, compares copied native/DH/cloud depths, builds a shared actor scene and writes into shader scene colour/material/normal plus live/opaque DH depth before pack composites. It assumes responsibility for the pack's camera, pixel grid and temporal processing. That is a substantial change in integration even when the model asset and pose are identical.

The legacy renderer returns early when external DH ownership is active. Keeping its code installed is not an A/B test. A fair comparison must verify which renderer actually drew the same vehicle under the same world, shader and zoom conditions.

## Required next verification, before declaring a fix

1. Capture a short stationary sequence with frame/pass identity, active renderer owner, actual actor transform, native/DH matrices and origins, effective FOV, target sizes, jitter inspection result/reason, frame uniform and applied offset. Compare pre-composite actor colour/depth with final output to locate whether geometry, visibility or postprocessing changes.
2. Correlate laser targeting/input, targetConfirmed, exact failed cell and registry state, query reason, client predicted point, server acceptance, and HUD projection for the same frames. Log transitions with a bounded rolling history; the exhausted 20-message quota cannot answer this.
3. Compare shaders off/on against zoom off/held/transitioning, in the same stationary view and on the same surfaces. Verify loaded terrain and missing-chunk terrain separately. Include vehicle camera, handheld/pod and relevant private/thermal views.
4. Establish one actor temporal policy with explicit handling of jittered geometry, depth lookup, material mask and history. Test final output across the eight-frame cycle, not only supplied matrix arithmetic.
5. Establish shared MCHELI laser semantics for loaded/native and stored EFR states. For each supported registry block/metadata and required neighbour configuration, compare hit/miss and first intersection against the native laser oracle. Include oblique/grazing rays, entry from inside, thin faces, soft plants, transparent full blocks and solid contents of waterlogged blocks. Missing required state stays UNKNOWN.
6. Run the same cases on client prediction and dedicated-server validation. A client-visible green marker alone is insufficient. Keep the existing server weapon/source/range/aim/first-obstruction checks.

Full EFR compatibility cannot be honestly claimed from terrain appearance or a few allowed block names. Stored DH data can be missing, simplified or insufficient to reconstruct neighbour/tile-dependent collision. Exact support requires the relevant state or another trustworthy source; otherwise the limitation must remain explicit.

Existing matrix/jitter unit checks and synthetic GPU depth checks remain useful component tests. They do not exercise the actual jitter discovery, final pack temporal image, zoom/HUD phase agreement, all EFR registry shapes, or network confirmation. No additional compile/build was run for this documentation-only audit.
