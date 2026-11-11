# Vehicle shader lighting — resumable work record

## Current status

Started 11 November 2026 after the user accepted the scope terrain/fog work in-game with and without shaders. **No vehicle lighting fix has been implemented yet.** Initial source diagnosis confirms a missing shader entity-lighting stage, not an established scalar brightness bug. Preserve this distinction when reporting progress. Update this file after each implementation/test milestone and before stopping.

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
