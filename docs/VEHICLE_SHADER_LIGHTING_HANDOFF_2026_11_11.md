# Vehicle shader lighting — resumable work record

## Current status

Updated 12 November 2026: **a production candidate is implemented, built and automatically checked on `fix/vehicle-shader-lighting` in DH, Angelica and FMUR. The user accepted the lighting result in-game on 12 November 2026 and authorized clean main/default-branch merges.** The original investigation below is historical context. The final implementation/test ledger and `VEHICLE_SHADER_LIGHTING_TEST_2026_11_12.md` describe the current candidate; no main promotion is authorized before user acceptance.

Keep future work bounded, without parallel agents or a broad renderer rewrite. A separate account/chat must be able to resume from this repository record. Never claim visual acceptance from component tests.

## Binding user constraints

- All Git status/diff/history reviews, branches, commits and pushes through **GitHub Desktop**, not Git CLI or connector substitutes. Source reads/edits/builds via shell are allowed.
- Other developers may edit the checkouts. Inspect current state in Desktop before including anything; preserve unrelated work.
- Use current version metadata and normal build counters, never old handoff versions or manually manufactured counter jumps.
- **WorldBorder must remain untouched and uncommitted.**
- Preserve shaders-off appearance (user says it is correct), accepted motion/ownership/depth behavior, colour PiP fog and active FLIR atmosphere bypass.
- The user has now authorized merging the tested integration branches into main, with vehicle shader lighting explicitly deferred. Future lighting implementation should use a feature branch and require its own acceptance.

## Accepted baseline

User says the paired fog candidates work well in game with and without shaders and they pushed them:

- FMUR `codex/scope-sky-weather`, commit `95345c5`, 1.8.06_X210-pip-scope-test.
- Angelica `codex/angelica-2.2.14-pip`, commit `e26984d7`, 2.2.14_X7.
- DH `feature/mchr-fmur-player-lods`, last known X71, earlier checkpoints `ae3a36778` / `4ece18fd2`. Verify current Desktop state; do not assume unchanged.
- WGQOL X51 weather replay. MCHELI last known 1.9.01_X212, dev metadata; EFR X10. No current changes requested there unless evidence requires them.

Fog acceptance and tests: FMUR `docs/debugging/scope-dh-fog-candidate-2026-11-11.md`; Angelica `docs/flans-scope-dh-fog-2026-11-11.md`. Baseline rollback commits and hashes are recorded there. Do not overwrite historical claims with new unverified acceptance.

## Reproduction

User screenshots `/Users/rhysh/Desktop/Screenshot 2026-11-11 at 22.36.44.png` (LOD) and `...22.36.40.png` (native). At night with shaders enabled, the MCHELI vehicle becomes much darker at the native/LOD transition. No shaders-off issue. Must consider ordinary PiP and FLIR-off colour optics too; active FLIR is a separate sensor view.

## Source diagnosis established

DH `dh/forge17/src/main/java/com/seibel/distanthorizons/forge17/effects/ActorRenderer.java`:

- `drawModel` around 330 calls `FmurPlayerBridge.beginModelCapture`, loads isolated view matrices, explicitly `GL20.glUseProgram(0)`, enables Minecraft lightmap and standard item lighting, configures texture combiners, sets `model.light()`, then calls `model.draw`.
- `cameraDraw` around 190 delegates to the same `drawModel`, so this affects the auxiliary model path too.
- Main actor flow captures each model into `ModelCapture`, composites into `ModelScene`, then submits its already-lit RGBA colour to the shader scene. Existing depth, occlusion, jitter and temporal material marker handling must be retained.

DH `actors/ModelCapture.java`, `actors/ModelScene.java`, and `dh/forge17/src/main/resources/assets/distanthorizons/shaders/actor_model.frag`:

- Capture stores RGBA colour and self-depth, no full native entity normals/material/light coordinates.
- Compositor preserves premultiplied colour, emits temporal marker 254 and a placeholder non-glossy normal. It does not run the native entity shader lighting.
- Auxiliary branch emits captured colour (or heat) under existing compressed-depth law.

MCHELI `src/main/java/mcheli/lod/MCH_ExternalVehicleRenderer.java` around 216:

- Uses tracked native vehicle where possible, otherwise visual actor; `setCommonRenderParam(definition.smoothShading,sampledLight)`, white colour, native model/common-part render, balanced cleanup.
- Samples native brightness for tracked entities and packet brightness otherwise. No evidence yet of a bad brightness value.

The local BountifulStars snapshot `/tmp/dh-bountiful-x45/shaders/program/gbuffers_entities.glsl` calls `DoLighting`, whereas these captures are vanilla lit. Main actor compositor injects into the pack's later colour target through `ShaderEffectsTarget`; merely applying a multiplier/gamma guess would not establish day/night/block-light/material parity.

## Next investigation / bounded implementation decision

Inspect FMUR `ThermalRenderApi.beginIsolatedGeometry` and its state guard, Angelica's entity program hooks and existing `FlansScopePass` / `ScopeRenderState`. Determine whether an isolated **entity shader capture** can reuse existing programs with a private output/depth without mutating primary buffers/history or requiring a general renderer rewrite. Establish output colour-space and stage contract first (including premultiplied alpha and avoiding applying pack lighting twice). If that cannot be bounded, report the concrete blockers rather than implementing an arbitrary brightness boost.

The fog fix's `ScopeDistantTerrain` is a terrain-specific extension; it does not automatically fix actors. FMUR `ShaderScopeScene.drawForward` still paints actors after finalization, so shader-native scope actor lighting needs an explicit compatible output contract as well. Preserve terrain's newly accepted pre-deferred DH submission.

## Tools and validation available

- Java 8: `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home`; Angelica Gradle launcher JDK25, source downgraded to Java8 by its existing pipeline.
- Use offline builds and meaningful focused checks; Gradle caches and GL driver access may need sandbox escalation.
- FMUR focused checks via `-I tools/player_lod_checks.gradle`; temp `/tmp/fmur-current-particle-check.gradle`; LWJGL2 natives `/tmp/fmur-nvg-natives`; WGQOL dev classes at sibling `WGQOL/build/classes/java/main`.
- Angelica `:test :glCoreTest`; root-task prefixes avoid accidentally filtering unrelated subprojects. Real GL tests have normal Launch/Iris/Minecraft fixture setup in `ScopeDistantTerrainGLTest`.
- `/tmp/angelica-scope-pack-check.gradle` enables actual local BountifulStars terrain/water compile check. A fresh machine/account must recreate optional temp files if absent.
- DH actor GPU fixtures and previous results exist in `dh/forge17/src/test` and repository tools; discover exact current commands before running. No lighting tests were run during this initial diagnosis.

## End-of-session ledger

Initial milestone: persistent handoff created; source diagnosis only. No production edits, builds, version bumps, commits, pushes or installations performed for vehicle lighting yet. This document itself is uncommitted until reviewed in Desktop. Continue updating this section with exact files, commands/results, failures, rollback points and next action.

### Follow-up inspection

FMUR `ThermalRenderApi.beginIsolatedGeometry()` deliberately suppresses ordinary Iris selection using `ScopeRenderCompatibility.beginEntityReplay`; auxiliary views rely on their enclosing isolation. Angelica `DeferredWorldRenderingPipeline.Pass.use()` binds pipeline-owned before/after-translucent framebuffers, and `rebindCurrentPass()` restores those expected targets. Therefore simply enabling the primary entity program inside `ModelCapture` is unsafe: subsequent render callbacks can rebind the main scene, and native uniforms/projection no longer describe the local depth-adjusted capture. Do not remove `glUseProgram(0)` as a shortcut.

The implementation boundary must explicitly own output FBO routing, projection uniforms and scope/main colour-stage semantics. Inspect `Pass.use()` around line 1337 and `ModelCapture.begin/end` before designing that seam. No production code changed in this follow-up.

### Implementation feasibility checkpoint (latest)

The user subsequently explicitly requested implementation and said they moved a copy of this handoff to Desktop. Continue treating the repository copy as authoritative; do not claim that an external Desktop copy updates automatically.

Inspected `FlansScopePass.supportsShaders()`: it rejects `main.isWorldPassActive()` and recursive `rendering`. DH's main actor callback executes before shader finalization (`DistantSmokeRenderer` around 441); colour scope actors execute while the private scope is already active. Thus wrapping each actor in existing `FlansScope.render` cannot work in either location without violating guards. That API also renders sky and executes full deferred/composite stages, which would alter captured alpha and apply tone mapping again when main actors re-enter the main HDR target.

Concrete contract required before production edits:
1. An Angelica geometry-only isolated entity pass, legal inside the existing main/private scene, using private colour/depth output and read-only shadow/sampler inputs; no full world, sky, deferred, composite or primary history advancement.
2. Local model projection/inverse supplied to entity-lighting reconstruction, with restoration after every renderer callback/failure and protection against Pass.use/rebindCurrentPass restoring a primary framebuffer.
3. Main actor capture outputs pack-native scene-linear colour to its existing pre-composite target. Colour PiP actor submission must happen before its composite, or explicitly convert that output through the matching private pack presentation stage. Current PiP actors are forward post-composite, so simply sharing an HDR capture is insufficient.
4. Shader-off and thermal capture keep their established model path. Retain premultiplied alpha, transparent parts, native/LOD ownership, solid/cloud occlusion and material-254 motion safeguards.

Do not weaken scope re-entrancy guards, swap the global public pipeline, use a per-actor full shader pipeline, or apply an arbitrary colour multiplier. No production implementation/build/commit performed at this checkpoint. The user constraint against a large renderer rewrite still applies; the above cross-stage work exceeds an obvious small lighting-state correction.

## Closeout authorization

The user accepted the scope behavior and explicitly deferred vehicle shader lighting. They requested committing this handoff into DH and merging the existing tested DH integration into main, alongside FMUR, MCHELI and WGMap. This records a known lighting limitation; it does not claim that limitation is fixed. Angelica branch/default naming is user-managed and must be left alone. WGCore and WorldBorder remain outside this closeout.

## Active implementation — 12 November 2026

Current request supersedes historical branch/closeout instructions above. No promotion to main is authorized until in-game acceptance.

- Computer control was initially still stopped; no work bypassed that restriction. User resumed access. GitHub Desktop verified clean synchronized DH `main` at `75b6a0c3e`, FMUR `main` at `95345c5`, and Angelica default `main/2.2.14` at `e26984d7`. Angelica's renamed local branch still showed “Publish”; a fetched comparison to `origin/main/2.2.14` showed Behind (0)/Ahead (0). Its naming/tracking/default configuration was left alone.
- Created `fix/vehicle-shader-lighting` in DH, Angelica and FMUR through Desktop. No MCHELI/WGMap changes currently required. No stashes restored, no commits/pushes/merges performed.
- Work in progress: Angelica `EntityGeometry`/`EntityGeometryPass` borrows the active scene's entity program/samplers, intercepts pass/FBO restoration during the geometry capture, overrides/restores capture projection uniforms, retains the public pipeline and skips all world/composite lifecycle calls. DH selects this only for shader main actors; capture/shared-scene colour changed to RGBA16F to retain HDR. Scope additive target/projection API and DH `beginShader` submit captured actors into private scene colour/material/normal and full/opaque DH depth before finalization. FMUR negotiates the extension; old companions retain their existing path. Active thermal path remains the old path.
- First compile attempts failed before compilation because Gradle cache locks were sandboxed; FMUR wrapper is non-executable so use `bash ./gradlew`. Retried builds with approved cache access. DH `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew --offline :dh:forge17:compileJava` PASSED. FMUR Java8 `bash ./gradlew --offline compileJava` PASSED. Angelica compile found missing `ShadowRenderingState` qualification, corrected; rerun pending. Logs `/tmp/vehicle-{dh,fmur,angelica}-compile.log`.
- NOT YET TESTABLE/ACCEPTED: implementation and tests are still underway. Need exercise callback FBO routing, restoration after failure, projection inverse, HDR/alpha, actual local pack compilation, scope actor/DH/native depth ownership, and existing motion/thermal/fog regression checks; then build paired artifacts with normal counters. No runtime/visual acceptance claimed.


### Final candidate ledger — 12 November 2026

Final matched runtime set: **DH 3.3.4_X73, Angelica 2.2.14_X10, FMUR 1.8.06_X211**, collected under `build/vehicle-shader-lighting-test/` with SHA-256 checksums, build/test logs and temporary fixture sources. All runtime classes were checked for Java 8 compatibility. The earlier “NOT YET TESTABLE” entry above records an intermediate milestone, superseded by this entry. The candidate is ready for the user's in-game test, not visually accepted.

Exact commands, hashes, failures, platform limits and the in-game checklist are in [`VEHICLE_SHADER_LIGHTING_TEST_2026_11_12.md`](VEHICLE_SHADER_LIGHTING_TEST_2026_11_12.md).

Files and reasons:
- Angelica `api/EntityGeometry.java`, `pipeline/EntityGeometryPass.java`, `pipeline/DeferredWorldRenderingPipeline.java`: geometry-only capture and program/FBO routing across primary/private pipeline callbacks, local projection/inverse restoration, listener restoration and idempotent close. Reject nested captures and unsafe storage/image/output contracts. The public pipeline is not swapped.
- Angelica `gl/blending/{AlphaTestStorage,BlendModeStorage,DepthColorStorage}.java`: suspend/restore effective raster override bookkeeping around private captures.
- Angelica `api/FlansScope.java`, `pipeline/FlansScopePass.java`, `compat/dh/ScopeDistantTerrain.java`: expose only the active pre-composite private actor targets and matching DH projection, without weakening world/scope reentrancy guards.
- Angelica `EntityGeometryGLTest` and `ScopeDistantTerrainGLTest`: real GL capture/isolation/HDR/alpha/projection/failure checks and actual local entity/terrain/water program compilation; configure the test fixture's shipped Iris macro default explicitly.
- DH `actors/{ModelCapture,ModelScene}.java`: RGBA16F captures/shared scene retain pack-native HDR scene colour.
- DH `actors/ShaderActorLighting.java`, `effects/ActorRenderer.java`: optional versioned Angelica capture integration; shader-off/thermal keep the existing path.
- DH `effects/{ActorCameraApi,ActorCameraPass,NativeSceneDepthCopy}.java`: shader colour-camera submission, immutable terrain depth sampling, private scene material/normal/depth writes, correct near-native opaque/no-hand depth and resource cleanup.
- DH `scripts/tests/actor_model_gl_test.c`: HDR night/day/block-light retention and near-native opaque/transparent depth regressions, alongside existing ownership/alpha/cloud/jitter tests.
- FMUR `AngelicaCameraBridge.java`, `DistantActorCamera.java`, `ShaderScopeScene.java`: negotiate additive shader actor APIs, submit colour actors before private finalization and avoid repeating them in the forward layer; old companion and active FLIR paths remain available.
- Each changed repository's `gradle.properties`: normal successful packaging counters only. DH handoff and candidate test document are the persistent record.

Sequential review findings resolved:
- B1: callbacks using the public primary pipeline could escape a private scope capture. A shared render-thread capture routing guard covers both pipeline instances; the GL test forces both rebinds.
- B2: inherited blend/alpha/depth override locks could corrupt offscreen coverage or deferred caller state. Explicit suspension/restoration added and inherited-lock GL checks pass.
- B3: DH near-depth clamping alone cannot preserve cockpit-range native fog/order. Separate opaque full/opaque/no-hand native depth writes were added without sampling their attached target; transparent parts do not write opaque depth. New production-GLSL checks pass.
- B4: direct model replay can inherit a held-item id and use item IPBR. Capture resets entity/item/overlay defaults, then restores caller attribution; GL checks cover restoration.
- B5: closing a stale capture twice must not clear a later capture, and glUseProgram alone does not restore dynamic uniform listeners. The completion helper checks capture identity and reconnects the enclosing pass's uniforms without rebinding its FBO.
- T1: Minecraft visual parity remains pending user in-game acceptance. The optional full legacy FMUR GL3 compatibility harness is unsupported in this macOS legacy context; the unchanged lighting subset passed on a valid explicit framebuffer and the independent core actor GPU suite passed. Full limitations are recorded in the candidate document.

Final passing validation: Angelica root 578 ordinary tests plus 28 GL cases (22 pass/six existing platform skips), Checkstyle and production build; actual BountifulStars entity/terrain/water compile; DH 215654 core assertions and build; 131 actor production-GLSL pixel/depth checks; FMUR 10110 core and 2030 presentation assertions plus server boundaries, 150 actual-DH companion environment checks, 15822 thermal and 303 particle checks, unchanged fixed-function lighting assertions on explicit FBO; 216 scope terrain/fog production-GLSL checks; FMUR build; all three jars' Java 8 bytecode checks.

No commits, pushes, merges, installations, global pipeline swaps, brightness/gamma multipliers, gameplay/packet/save/content changes, WGMap occlusion retirement, stash operations, branch/default renames or edits to excluded repositories were performed. Work remains uncommitted on the three requested fix branches. Next action: test the matched set in game against the checklist, record acceptance/failures and iterate on these branches. Only after user acceptance consider Desktop commits/pushes/main promotion as separately authorized.


## User acceptance and merge authorization — 12 November 2026

The user reports the lighting fix worked in-game and explicitly authorizes merging the lighting changes into each mod's main/default branch. This accepts the lighting result generally; it does not assert every checklist scenario was individually tested. Preserve the recorded automated/platform limitations. Perform all commits, synchronization, comparisons and merges through GitHub Desktop. Preserve Angelica's current `main/2.2.14` naming/default and unrelated local work/stashes.

A separate minor issue is now reported: apparent tank-track/LOD-ground z-fighting. Investigate and fix this only on a new `fix/lod-zfight` branch based on the current merged/synchronized main/default of repositories requiring changes. Do not fold an untested depth/terrain change into the accepted lighting merge.
