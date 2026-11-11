# DH visual acceptance and lighting follow-up

> **Latest runtime status — 11 November 2026:** X68/X205 is NOT accepted as a shader/Angelica-zoom fix. The user reports smooth-enough unzoomed behaviour, but Angelica zoom still disrupts laser designation, particularly with shaders. Shader vehicle jitter also occurs while stationary. Historical fixes and automated passes below must not be read as resolving these OPEN failures. Continue from [DH_HANDOFF_2026_11_11.md](DH_HANDOFF_2026_11_11.md). Missile firing/watch tests remain deferred at the user's request.

10 November 2026. The user's X57 world test confirms stable MCHELI vehicle visibility under movement, zoom and FOV changes, terrain occlusion, a clean 750-block visual range, and rapid native/LOD boundary crossings. A small vertical change during handoff remains visible. This acceptance covers that scene, not every animation, player or shader view.

## X59 screen-edge ambient occlusion correction

The user reports brightness flashes mainly along the bottom and sides with shader packs **off**. The installed profile has DH SSAO enabled. The original SSAO blur allows texture sampling outside the viewport, and its intermediate texture has the default repeat wrap mode. A real GPU fixture reproduces opposite-edge contamination: an otherwise bright edge pixel becomes approximately 0.640417 instead of 1.0. The original AO stage also fails forward/reverse physical-scene parity (one tested value 0.94946 versus 0.928944).

The corrected AO stage rejects off-screen/behind-camera samples, recognises clear sky in the current depth direction and guards degenerate normals/sample vectors. The bilateral blur rejects off-screen and sky samples. It reconstructs physical depth from the actual inverse terrain projection and hardware depth range. This is necessary because the reverse DH projection can occupy only part of the hardware depth range; a simple reversal of the near/far formula is insufficient. The previous independently calculated near/overdraw distance also need not equal the near plane of the rendered projection.

GL and Blaze SSAO source math and uniform upload layouts were updated together. The Forge 1.7.10 GL path was compiled and exercised; no modern Blaze game runtime acceptance is claimed.

Validation:

- 54 SSAO GPU checks passed: viewport edges, sky/terrain boundaries, physical depth discontinuities, FOV 70/30/4, forward/reverse depth, both hardware depth ranges and the partial-range reverse projection used by DH.
- The same edge fixture fails the original blur. The physical-scene parity fixture fails the original AO stage.
- 64 actor GPU depth/cloud checks still pass. The installed BountifulStars TAA fixture still passes its moving-body/fresh-smoke material-marker checks.
- 214,215 component assertions pass with installed Angelica 2.2.14 and the compile-time fixture. Full core suite: 121 tests, 27 suites, no failures.
- Final artifact: `build/libs/DistantHorizons-3.3.4_X59.jar`, SHA-256 `ded60ac101f42ead005b6c9ef2bfa504be123b85132a0dc9404e59f1649fa056`.

X59 was installed only in the integration-test instance after the user closed it and the game JVMs were verified absent. X57 is archived in `dh-lods-test-archive-metadata/before-X59-2026-11-10/`. The installed hash and single-active-DH-jar checks passed. No server/companion protocol change. The reported flashes still need a world retest; the fixture establishes real defects, not that every reported lighting artifact shares their cause.

## ChickenChunks investigation: open

Inspection of the exact installed ChickenChunks jar establishes metadata-specific bounds: the main loader base is 1 by 0.75 by 1 blocks; the spot loader occupies x/z 0.25..0.75 and y 0..0.4375. Additional visual elements are rendered by a tile-entity renderer. DH's 1.7.10 texture path resolves face icons directly, without baking these partial model bounds; the terrain representation uses integer-height voxel columns.

The user locates the oversized/raised/doubled appearance near a LOD transition but cannot yet distinguish actual native/LOD overlap from the simplified shape becoming visible. Neither an extra rendering pass nor an incorrect native block position has been demonstrated. ChickenChunks geometry, ticking and chunk loading were not changed. Exact shape parity requires a deliberate partial/custom-model representation; it cannot be established by changing the face icon or shifting the whole LOD upward/downward.

Next reproduction: keep a normal full cube beside each loader variant, approach/recede with a stationary camera angle, and compare the first appearance of LOD geometry with the native base/orb. Record a close view, overlap view and distant view at the same orientation. This distinguishes duplicate coverage from voxel-shape simplification before selecting a fix.

## Vehicle handoff and lighting: open checks

Tracked DH vehicles reuse native entity state and MCH_ClientEventHook.smoothing, which is assigned the render event's partial tick. Snapshot-only vehicles use the authoritative buffered pose. The remaining small vertical change needs measurement across **native draw / DH with tracked entity / DH snapshot-only** transitions. Native network position quantisation or a difference between tracked interpolation and the authoritative pose are hypotheses, not confirmed causes. No constant height correction was added.

For ordinary shader-off lighting, the actor pass enables the current native lightmap, normal normalisation and standard entity lighting. MCHELI's model wrapper receives tracked brightness when the entity exists and snapshot brightness otherwise; the normal live model and adapter both set white model colour after common setup. This source audit does not establish daylight/night/block-light parity by image comparison, including destroyed/special-status presentation.

Shader support must be assessed separately. Existing actor tests verify projection, native/DH terrain occlusion, cloud masking and the installed pack's temporal material marker. The current actor pass captures already-lit model colour and composites it into the supported late scene target. It does **not** submit the native entity G-buffer's full normals/material attributes or add a model shadow-map pass. Native shader lighting/material/shadow parity is therefore not established and must not be advertised as complete. Full-scene BountifulStars acceptance remains open. Shader tests here do not imply support for arbitrary shader packs or auxiliary scope/thermal views.

## Recommended manual sequence

| Priority | Test | Expected result |
| --- | --- | --- |
| 1 | X59, shaders off: sweep zoom/FOV while looking at the lower corners and sides over terrain | No new edge flashes; real surface shading remains; compare the same scene with DH SSAO temporarily off only if flashes persist |
| 2 | Close/distant stationary vehicle in daylight, night and torch/block light; rotate the camera around it | Stable world-relative shading and a consistent brightness transition, without a camera-dependent change of light direction |
| 3 | Observer plus second account driving Mi-8/Abrams; observer crosses native draw and entity-tracking boundaries | Continuous movement and pose with one model; note the precise boundary of any vertical step |
| 4 | Second account: engine off/on, rotor start/stop, turret yaw/pitch, wheels/tracks, landing gear, hatch/canopy, applicable wing/nozzle states | Distant model follows actual source state after it becomes snapshot-only; no invented rotation, stuck animation or pose jump |
| 5 | Second account on foot: walk/sprint/sneak/jump/swim, turn head/body, use ordinary items and FMUR gun/reload/aim/fire actions | Body, equipment and gun stay on the same animation timeline across tracking exit; no detached gear or duplicate actors |
| 6 | Place/remove a wall between observer and distant vehicle, including rapid small updates | Existing displayed terrain remains usable while rebuilding; new mesh changes per-pixel occlusion when displayed, without hiding the whole vehicle solely because a rebuild is pending |
| 7 | Repeat baseline and lighting tests with the installed BountifulStars pack enabled, then clouds on/off, glass/rotors and silhouettes against terrain/sky | No depth leaks, disappearance, temporal trails or opaque transparent parts; separately record model brightness, sun/shadow and material mismatch |
| 8 | ChickenChunks main loader and spot loader beside full cubes, through the terrain transition | Establish whether extra geometry is overlap, shape approximation or position drift; preserve the capture for a targeted geometry fix |

A second account is important for observer-visible movement and player poses, and for testing true snapshot-only presentation. Entering a vehicle on the observer's own account does not test the same state delivery or viewpoint. Dedicated-server testing is required for multiplayer acceptance; headless protocol/model tests are only component evidence.

## Multiplayer follow-up and X60

The user tested with a second account and reports that held FMUR guns and NVGs appear correctly on distant players; observed MCHELI vehicle animations and vehicle-destruction explosions also appear correct. These are accepted observations for that content/scene, not blanket acceptance of every animation and attachment. Third-person ADS is absent in the native FMUR presentation too; the user explicitly defers that larger animation change. Player and vehicle thermal LODs remain missing.

The user supplied a first-person M1A1HC Abrams screenshot showing terrain where the nearby tank hull should occlude it, alongside a report of shaking/stuttering while riding. The old radius/chunk-distance handoff could replace the locally ridden model even while the camera was inside it, particularly at low native render distance. Distant actor capture uses a separate near/depth/composite path; it is inappropriate for the cockpit model.

X60 asks MCHELI's existing `MCH_EntityAircraft.getAircraft_RiddenOrControl(Entity)` resolver for both the local player and render-view entity. This existing resolver includes direct aircraft mounts, passenger seats via their parent aircraft, and UAV stations via their controlled aircraft. The resolved vehicle cannot be cancelled by the native handoff and cannot enter the distant model collection. Native observation also accepts it independently of the generic distance/radius threshold. No live vehicle pose, physics, camera or render cache is overwritten. This fixes a confirmed inappropriate ownership route; the precise in-game shake/depth symptom still needs retesting.

Player LOD capes now default off via the file setting `enablePlayerLodCapes=false`. Both `hasCape()` and the cape-resource getter honour the option only on VisualPlayerActor, leaving nearby native players alone. Explicit opt-in remains possible, but cape motion is not synchronised and therefore is not accepted as correct.

Path side colour is a separate issue from partial-block shape. Previously the atlas normalised every face relative to its own face average, then multiplied it by the representative LOD block colour. Uniform dirt sides could therefore collapse to the path-top colour. EFR `grass_path`/`dirt_path` and vanilla farmland now use the representative top colour as a shared ratio reference for all their face textures, preserving the distinct side/bottom colours. The same blocks are classified as dirt material. Default texture handling for other blocks remains intact. This uses the active face sprites, including resource-pack replacements, and requires no world/cache deletion.

Exact path height (15/16), slab halves, stair geometry, ChickenChunks bounds and arbitrary modded partial shapes remain open. The existing terrain data/mesh uses integer vertical columns; correct sub-block geometry needs a deliberately supported shape/vertex representation and corresponding shader/depth integration. The side-colour correction does not claim to implement those fractional shapes. Avoid guessed whole-block offsets or changing native collision/loading behaviour.

Thermal diagnosis: ActorRenderer excludes FMUR auxiliary views through `FmurPlayerBridge.auxiliaryView()`. Meanwhile ownership takeover suppresses the legacy far presentation. A dedicated DH actor colour/heat/depth view contract is required; reusing the primary-view framebuffer or simply drawing through missing terrain is not a valid thermal fix. No thermal support is claimed for X60.

Validation: 122 core tests across 28 suites and 214,227 component assertions pass with both Angelica fixtures. New tests exercise local-player/view-entity ownership, dismount, resolver failure retaining native rendering, production handoff/collection guards, default/disabled/opt-in cape handling with a real cape resource, and the distinct path-side ratio reconstruction. Artifact `build/libs/DistantHorizons-3.3.4_X60.jar`, SHA-256 `37a89b6959e98e0a3bfacd3176fc0ac48f40bec5e08ab222384f043dd55be2f9`. No server, companion or wire-format update is required.

After the user confirmed both account clients were closed, all four known test JVM processes were verified exited. X60 was installed in the integration-test instance and its hash verified. X59 is archived at `dh-lods-test-archive-metadata/before-X60-2026-11-10/`. Exactly one DH jar is active. No automatic relaunch was performed; cockpit, cape and path-colour world acceptance remains pending.


## X62 acceptance queue

X60 accepted by user: native tank/Ka52 first-person view, path side colour, cape default-off.

1. Two-account idle player across native/LOD boundary: look/head fixed, torso must keep its native angle. Repeat while walking, strafing, sneaking and turning; check both handoff directions.
2. BVR on loaded and detached Mi8/Abrams: box/text remain over the target; pan across cockpit frame/opaque model parts, hills and newly built terrain. Repeat normal FOV/Angelica zoom and 2/8 chunk native distance. Check partial cover, unknown LOD coverage and rebuild continuity. Off-screen arrows retain existing behavior. Native target-envelope occlusion is conservative; inspect cover immediately beside the target.
3. Colour PiP and thermals with both accounts: player equipment, vehicle animations, partial visibility, near cover, distant terrain, rapid ADS open/close, resize and transitions. Compare near/far actor depth and heat silhouettes. Check local vehicle is not self heat. Zoom terrain should refine using existing DH quality settings rather than stay untextured; allow asynchronous mesh refinement.
4. Fixed WGMap waypoint and MCH GPS on one world coordinate: strafe, fly, change FOV/zoom and render distance. Confirm anchor stays put and label sizing stays stable. Third-person camera changes are owned separately.
5. Toggle each of MCHELI/NTM/FMUR/Player LOD support independently. Check saved false values remain respected and capes remain disabled on distant players.
6. Repeat the supported shader-pack path separately. CPU/GPU component checks do not establish shader pack G-buffer/material/shadow parity or complete in-game private-camera acceptance.

Open: exact ChickenChunks/other partial geometry, shader-native actor material/shadow parity, Flans third-person ADS animation, and full migration of WGMap CPU first-hit/laser consumers. Existing radar/lock/gameplay authority and third-person camera code were preserved.


## X65 retest

Confirmed by user: vehicle thermal silhouettes with shaders, idle player torso handoff. New set: DH X65 / MCHELI X203 / FMUR X209 / WGMap X163-migrate-lods.

- Shaders: place player/vehicle behind near solid walls and DH hills; test complete/partial occlusion, zoom, first-person cockpit and held item cover.
- PiP/thermal: enter/leave ADS while looking at close normal blocks outside the lens. The main view must retain its matrices, terrain quality and visibility. Recheck native/distant player and vehicle silhouettes inside the lens.
- BVR: target visible above LOD ground, label displaced below it. Text must stay whole over near-target ground while real foreground/cockpit/large terrain cover still masks it.
- Trees: compare freshly sampled oak trunks/leaves with nearby native geometry and PiP. Old lossy cached records may require terrain refresh; no DB was deleted.
- Beams: native and distant points, Y=320/500, camera below/above the waypoint, Beam below OFF, Sky extent ON. Confirm no lower endpoint drift and an upper endpoint above the waypoint. Distant beams defaults OFF; opt in to check physical angular width rather than a large nearby projection. Icons/labels remain independent.
- Lasers: matched DH/MCHELI on server and client, existing finest DH terrain beyond native chunks, with WGMap unavailable in an isolated test. Check first wall/hill, missing corridor, diagonal thin wall, live block changes, loaded target vehicles and rangefinder. Unknown data must refuse rather than fabricate a surface. Stored map GPS Y/coordinates must remain unchanged.

The WGMap branch is based on main and retains legacy occlusion APIs for existing consumers; this patch removes MCHELI laser use of those APIs when DH is present, not the entire WGMap occlusion module. Full removal of other consumers is a separate migration step.

## X66: beam visibility, actor reflection response and trunk reduction

- WGMap X165 now hands off absolute world beam geometry; Terrain hides beams ON compares each fragment with native/DH terrain, OFF bypasses both. Test partial hillside cover, high Y, zoom and shaders. The BVR marker API_VERSION=1 and its target-anchor behavior remain intact. The added drawBeam method is an optional presentation seam, with no terrain queries or network authority. Captures use bounded scissor rectangles and do not alter scene depth.
- Actor LODs now replace colortex4's inherited terrain normal/fresnel in the existing BountifulStars/Complementary contract. Disable indexed blending for this output and restore its prior state so zero fresnel actually replaces wet terrain's response. This is not a claim of full native shader entity-lighting parity: captures remain lit through the isolated renderer. Compare stationary vehicles across native/LOD handoff, rain/water background, day/night, camera motion and thermals.
- RenderDataPointReducingList protects WOOD/LEAVES boundaries during preferred vertical merging, following the earlier texture-selector/full-data occlusion fixes. Forced very coarse budgets still simplify geometry; already lossy cached full data cannot recreate missing trunks. No cache deletion or format change. Test newly placed oak pillars and fresh trees as well as existing terrain refresh.

Review: B1 wrong beam-toggle semantics fixed; B2 inherited terrain reflection response fixed; B3 preferred reducer merging canopy down over trunks fixed. No new server/common client imports, packets, storage identities or chunk requests. Runtime shader parity and cached-data refresh remain manual acceptance items.

## X67 follow-up: shader depth pair and designation refresh

The user confirmed terrain/tree rendering is fixed on X66, but rapid MCHELI shader flicker remains. X67 publishes solid actor pixels into both the live DH depth and its opaque depth, matching the installed shader's dhDepthTex/dhDepthTex1 distinction. A separate immutable terrain snapshot remains the occlusion input throughout both writes, preventing attachment feedback and retaining physical terrain/cockpit rejection. Alpha-bearing actor pixels below .999 do not overwrite opaque depth. Extra work is one bounded full-view depth snapshot plus one shared actor depth pass, not one per vehicle. This does not claim native shader lighting parity.

B1: DH laser data previously discarded every 2 seconds, creating known/unknown designation gaps. Entries now start bounded asynchronous refresh after 1 second while retaining the current snapshot only until the original 2-second validity deadline. Missing/unsupported replacements invalidate immediately; slow reads still fail closed. Limits remain 32 sections, 4 requests and 1 adoption per 50 ms. No new chunks, world generation or networking. Server update to DH X67 is required to apply this common targeting-cache correction server-side.

B2: actor opaque-depth mismatch corrected as above. B3: MCHELI's green designation marker now uses the captured model-view eye for clamped position, retaining angular glyph size under camera translation/zoom. MCHELI X204 is client-only for this change; prior packet/API identities unchanged. Review is ACCEPTABLE for focused tests, with live shader/zoom acceptance pending.

Missile diagnosis: the recommended APKWS content does NOT set enableChunkLoader, whose default is false. Existing projectile forced-chunk implementation is conditional on this setting; this is not a DH terrain loader. Native MCHELI projectile body drawing also has a bounding-box-based range (typically 256 blocks for a 1-block box) and server TV-missile entity tracking is registered at 530 blocks. The existing DH MCHELI mixin relays smoke/explosion particles, while its dhBody proxies handle NTM models. Therefore smoke visibility alone does not establish full MCHELI missile-body support beyond client tracking/far-plane limits. No global chunk policy or entity tracking range is changed in this correction. A complete MCHELI missile-body migration remains separate required work if the user wants the same guaranteed beyond-chunk body visibility as NTM. Server /mcheli mslwatch on 5 can establish whether APKWS dies at an unloaded chunk or continues beyond the client's model visibility.

Manual acceptance: shader vehicles stationary and moving across native/LOD handoff; daytime/nighttime; designation continuously held for >10 seconds; Angelica/MCHELI zoom; native block newly placed across the laser; valid and unavailable DH terrain. Confirm server X67 and client X67 before comparing laser refresh.

X67 validation/install: `./gradlew :dh:forge17:checkDistantEffects collectJars --offline` PASS (214,285 component checks); CGL actor shader fixture PASS (104 checks including opaque/transparent classification); MCHELI `./gradlew test jar -Pbranch=dh-lods-test --offline` PASS (249 tests, production reobfuscation); both repositories `git diff --check` PASS. Installed client DH X67 and MCHELI X204 only after confirming no Minecraft JVM. Backups and hashes: `dh-lods-test-archive-metadata/before-X67-MCH204-2026-11-11-012846/install-manifest.json`. Extracted MCH folder name/content preserved; all 874 class/metadata entries verified. WGMap X165 and FMUR X209 unchanged. No server deployment or runtime validation performed.

A single-weapon APKWS chunk-loader override was prepared for review at `/Users/rhysh/Documents/GitHub/SRC-MCHELI-R-WDG-Edition/build/test-content-overrides/salagm_ah64e_apkwsiv70mm.txt`; it adds only `EnableChunkLoader = true`. It was not applied to the client or server; global projectile defaults and world-loading policy remain unchanged. Server weapon settings must be deliberately updated before testing APKWS physics in server-unloaded chunks.

## X68 / MCHELI X205: moving shader actors and partial-block designation

Scope follows the user's latest test: designation first, no missile firing/watch or chunk-loader policy changes.

B1: Installed BountifulStars vertex shaders add an eight-frame XY jitter after multiplying the camera projection. Actor captures/depth reprojection previously omitted that shift. ShaderActorJitter now reads the actual DH solid program's active framemod8/view dimensions and compiled vertex source, checks the known offset table/strength, and applies the same clip translation to actor capture, native reprojection and DH reprojection. The native terrain provenance corridor remains conservative. Unknown shader patterns use no guessed offset. This uses the public getSolidShader seam plus its read-only private id: actual Angelica 2.2.14 javap inspection confirms no public program-handle getter (only uniform-location access). No shader uniforms, pipeline stores or frame counters are mutated. Source inspection is limited to 1 MiB once per program/pipeline; three uniform reads per main frame. Runtime log prints detected jitter strength once. Full shader entity-lighting parity remains unclaimed.

B2: The laser snapshot previously rejected every non-full shape. It now carries exact sixteenth-height intervals for EFR paths/farmland, top/bottom slabs and snow layers, and Forge 1.7.10's six-half-cube stair ray shape for all eight metadata orientations. The DDA tests actual surfaces and passes through their empty space; edge supercover and unknown-prefix rejection remain. Original Forge BlockSlab, BlockSnow and BlockStairs sources and EFR BlockDirtPath were inspected. Unrecognised custom/tile-dependent shapes still require real data rather than an invented full cube. Cached interval encoding changes only RAM snapshots, not saved terrain, packet layouts or API_VERSION. No chunk load/generation is added. Initial in-memory reads may now be adopted immediately instead of an unconditional unknown frame.

B3: MCHELI guidance logic used RenderManager's previous rendered frame coordinates during logic ticks. X205 uses the current render-view entity position (same-world user fallback). FOV remains a projection concern and does not change the world-space ray. The existing server range/aim/weapon/LOS checks remain authoritative; no 550-m content setting or 1500-m legacy designation policy is changed.

Automatic diagnostics: at most 20 unknown-query messages per world, no more than one per second. Reasons: 1 pending initial snapshot, 2 missing column/interval, 3 unsupported cached shape, 4 expired snapshot awaiting refresh, 5 request budget, 6 missing/unsupported snapshot. Each includes the first rejected cell and up to 16 unsupported block-state candidates seen in that world's snapshots. Candidate names are not asserted to be the exact rejected cell's material. This avoids requiring chat commands while zooming.

Review: ACCEPTABLE for focused automated checks. Client code changes remain in the effects render path / existing SideOnly guidance method. Common ray code imports no client/GL classes. Java 8/Forge mapping compilation passes. No packets, persistence format, server permission policy, weapon damage or chunk-loading defaults changed. Custom slab/stair subclasses overriding native ray geometry and unavailable finest data remain compatibility limits requiring observation rather than false-positive designations.

Validation: `./gradlew :dh:forge17:checkDistantEffects :dh:core:test collectJars --offline` PASS: 215,383 component checks and 126 core tests. CGL actor harness PASS: 108 GPU checks, including mismatched-versus-matched native/DH jitter pixels. MCHELI `./gradlew test jar -Pbranch=dh-lods-test --offline` PASS: 249 tests plus production reobfuscation. Both diffs pass whitespace checks. Initial compile caught a mapping-name mismatch for canCollideCheck; that unused broad classification was removed instead of relying on an unverified method.

Installed DH X68 and MCHELI X205 after confirming no Minecraft JVM was running. Previous files/hashes are in `dh-lods-test-archive-metadata/before-X68-MCH205-2026-11-11-020247/install-manifest.json`. All 874 MCHELI code/metadata entries match the build. The currently named extracted folder `MCHELI-R-WDG-Edition-1.1.8_X204` was preserved; no content or weapon config changed. WGMap X165 and FMUR X209 unchanged. Server deployment and live shader/laser acceptance remain for the user: update BOTH DH X68 and MCHELI X205 server-side (strict MCH version check), hold designation across ordinary/partial terrain within 550 m with shaders off/on and zoom, then strafe/zoom around a stationary vehicle. No missile firing is needed. No commits, releases or source archives created.
