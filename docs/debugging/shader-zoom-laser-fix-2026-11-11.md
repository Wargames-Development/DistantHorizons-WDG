# Shader actor stability and EFR laser compatibility candidate — 11 November 2026

Candidate builds: DH **X70**, MCHELI **X206-dh-lods-test**, EFR **X10**. This implements the bounded corrections following the [diagnostic audit](shader-zoom-laser-audit-2026-11-11.md). The original X68 stationary jitter/Angelica-zoom reports remain the in-game acceptance cases; no visual acceptance or firing test is claimed.

## Changes

- `effects/ShaderActorJitter.java`: inspect the active pipeline's option-preprocessed ProgramSource, the same input Angelica supplies to its DH terrain program. Retain the checked eight-offset pattern and actual program uniforms; do not guess frame counters or edit shader packs. Log the detection reason alongside strength so unsupported input is distinguishable from a working match.
- `effects/ActorRenderer.java`: keep model capture, bounds and scene reconstruction on the stable native pixel grid. Apply the pack offset only to the native/DH projections used for depth sampling. This preserves the existing material-254 choice to bypass pack TAA without deliberately moving actor colour each temporal phase. Existing cloud/depth integration remains.
- `terrain/RayBlockShapes.java`: use native metadata-driven ray-selection bounds rather than opacity/render-type approximations. Full cubes, EFR leaves/roots, transparent cubes and oriented partial blocks can now be classified independently of how they render. Check native ray overrides; custom implementations require an explicit box contract. Save/restore mutable Block bounds and retain copied primitive boxes. The world/connection-owned cache is bounded to 4,096 block definitions and 3,063 nontrivial shapes; at most 16 boxes per shape. Unsupported input remains unknown.
- `terrain/TerrainRayWalk.java`: dispatch intersections through the selected shape provider while retaining the existing bounded voxel/supercover walk, built-in height/stair codes and unknown-prefix rejection. Snapshot encoding is internal memory only; saved DH data is unchanged.
- `terrain/DhTerrainRayApi.java`: use these native shapes, prefetch later corridor sections after an unknown result within the existing four-request budget, and adopt the oldest completed snapshot within the existing one-conversion budget. Keep the two-second validity limit. Continue rate-limited diagnostics beyond the former exhausted 20-message quota. Optional linkage failures cannot extend the validity of an old snapshot.
- MCHELI `MCH_LaserTargeting.java`: ignore the two EFR dry-grass registry identities consistently on the native client/server laser path, matching the DH path. Do not make arbitrary transparent or plant-material blocks passable.
- EFR `ModernMapParityBlocks.java`: provide optional `getDistantLaserRayBoxesV1` for its custom modern model blocks. Reuse EFR's own native selection geometry, including workstation multi-box geometry; do not substitute entity movement collision or render bounds. Tile face masks without supplied tile state remain unavailable.

No weapon/range policy, target packet, chunk-loading policy, saved-data format, shader-pack option, FMUR/WGMap code, third-person camera work or WorldBorder file was changed. No commit or push was made.

## Verification

- DH focused effects checks: **215,647** assertions passed, including native ray comparisons, invalid/missing state, optional linkage failure and oldest-first conversion budgeting.
- DH core: **126 tests / 30 suites**, zero failures/errors/skips.
- MCHELI: **249 tests / 50 suites**, zero failures/errors; two existing skips. Includes the dedicated-server optional-mod boundary and matching dry-grass native policy.
- Native/EFR comparison fixture: **51,946 checks** passed. It compares native hit/miss and first intersection with stored shape intersections for EFR azalea leaves, mangrove roots, amethyst variants, and **3,424 modern metadata states**. The fixture uses the real EFR class methods and definitions with isolated state, bypassing constructors/registration; it is not an in-world registry or pack launch. Enum entries which create other native block classes are excluded from that modern-model count.
- **240 modern metadata states** (15 definitions across 16 values) explicitly remain unknown in that fixture: lectern, sculk vein, pale-oak fence/button, pale moss carpet, resin clump, resin-brick wall and eight copper-bar definitions. These need additional state or geometry extending outside the current voxel. Native loaded-chunk collision continues normally.
- Actor OpenGL fixture: **124 checks** passed, including stable colour over the eight jitter phases in capture/shared-scene stages, per-pixel cover, copied depth/samplers, private cameras, clouds and actor surface buffers.
- DH/EFR production compilation and production remapping/packaging passed. MCHELI compilation/tests/reobfuscation/production packaging passed. All three final jars passed archive CRC checks; optional exported API/new shape class presence was verified. Diff whitespace checks passed.

Commands/logs: DH `:dh:forge17:checkDistantEffects :dh:core:test --offline`; MCHELI `test --offline` with Temurin Java 8; EFR `compileJava --offline`; CGL actor fixture compiled with clang and OpenGL. Packaging used DH `collectJars`, MCHELI/EFR `assemble`. MCHELI was packaged with the explicit `dh-lods-test` suffix. DH X69 was an intermediate candidate; the final optional-linkage hardening is in X70. Automatic successful package counters now read DH X70, MCHELI X206, EFR X10.

## Runtime acceptance still required

Use the same stationary vehicle/view with shaders off/on and Angelica zoom released/held/transitioning. Keep a fixed target and compare the final image, including native/DH terrain edges. The first shader detection log should state its source/reason; zero strength must not silently be described as an active correction.

Test laser designation before firing anything: ordinary ground, paths, slabs/stairs, azalea leaves, mangrove roots, amethyst hit faces and empty space beside clusters, and terrain behind dry grass. Compare loaded terrain with available finest DH data, within the selected weapon's range (the reported Z-10W CM502KG case is 550 m). Check stationary and slowly panning zoom, plus relevant pod/handheld/thermal views. Verify server acceptance as well as the green marker.

Missing finest data and unsupported neighbour/tile shapes still reject designation beyond the unknown prefix. These changes do not claim universal exact collision from simplified or unavailable stored terrain. The exact residual zoom-specific cause, if any, requires the new candidate's runtime result and logs.

Only the closed **Wargames DH LOD Integration Test** instance is targeted for client installation, with backup and hash verification. The server is not modified: it needs matching MCHELI X206 version strings and matching DH/EFR implementations to exercise the same distant-terrain semantics. EFR X10 is built from the current clean X9 source baseline plus this API; it also retains X9's already-committed import-height correction, whereas the test instance previously had X8.

## Confirmed client installation

Installed DH X70, MCHELI X206-dh-lods-test and EFR X10 in the closed integration-test instance. Standalone jar SHA-256 values match the packaged artifacts; all 874 MCHELI code/metadata entries match its jar and all 2,634 pre-existing content/other files retain their original hashes. The extracted MCHELI folder was not renamed.

Backup and restore manifest: `/Users/rhysh/Library/Application Support/PrismLauncher/instances/Wargames DH LOD Integration Test/dh-lods-test-archive-metadata/before-X70-MCH206-EFR10-2026-11-11-040158/install-manifest.json`. The backup contains the previous standalone jars, previous MCHELI code/metadata, and copies of all three new candidate jars. No client launch, server installation, firing test, database deletion or shader configuration change was performed.
