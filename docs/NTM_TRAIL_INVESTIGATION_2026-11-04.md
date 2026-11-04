# NTM trail investigation — 2026-11-04

Status: user confirms visual defect persists after verified X16 startup; root cause unproven.
Initial inspection was read-only. After independently verifying X16 and receiving renewed visual failure confirmation, bounded diagnostic-only DH instrumentation was added for X17. No source-mod or server changes were made.

## Version evidence

- Initial inspection: Minecraft child PID 10760 started 02:40:17 local (Europe/London).
- `fml-client-latest.log` startup examines X15; lwjgl3ify child log explicitly reports metadata `3.3.4_X15`.
- Flights at 02:51–02:52 still report `HBM handoffs=...`, the X15 diagnostic.
- X16 packaging occurred later (Gradle worker started 02:46:52); installed jar timestamp is 02:47.
- Disk artifact and installed `DistantHorizons-3.3.4_X16.jar` share SHA256:
  `dea3ef7122d0eb6e78a808c2a6b91f4d196cf93a097847ea962ed308e72293df`.
- Installed X16 metadata says `3.3.4_X16`. Its TrailClient class contains `NTM native emitter=preserved` and does not contain `HBM handoffs=`. TrailClient and HBM wrapper class major is 52.
- Therefore the initially running session and available flight history exercised X15, despite X16 being installed on disk. This is not evidence that X16 solves the defect; it prevents attributing these flights to X16.
- Client installed: HBM WG2.4.2, Angelica 2.2.14, MCHELI X194, FMUR X192. `config/angelica-options.json` has `advanced.enable_deferred_batching=false`.
- Existing server panel file manager shows `DistantHorizons-3.3.4_X10.jar`, HBM WG2.4.2, MCHELI X194 and FMUR X192. Root lists Crucible staging 667a28e dev-280. These are installed filenames, not an independent bytecode/loaded-server-version verification.
- UI inventory did not expose the running Java game window. No launcher was opened and no second game was launched.

## End-to-end path in current X16 source

1. HBM EntityMissileBaseNT client onUpdate invokes spawnContrail. DH MixinHbmTrailSource wraps attribution and always invokes the original. The server-tail adapter emits only reviewed implementations; custom xenon remains excluded.
2. TrailRelay observes server segments; two-world-tick batches carry source ID, endpoints, source tick, count/style/scale/thrust. No predicted flight or added chunk loading.
3. TrailClient consumes at most two queued batches/tick, filters world/range/age and trims against actual native emission markers with matching numeric entity ID (HBM marker age at most two world ticks). It invokes installed reflective source factories under remote attribution, capped at 16 births/sample and 128/tick.
4. HBM ClientProxy constructs ParticleRocketFlame with supplied 60–79 tick maxAge and atmospheric-pressure adjustment. HBM owns motion, aging, colour/alpha and expiry. DH ages late remote births by up to ten ordinary onUpdate calls. No separate retained smoke lifetime.
5. MixinEffectCapture attributes births and performs age correction. Real EffectRenderer dispatch is patched after mixin redirects. Supported particles inside camera side planes/range may bypass Angelica viewport rejection, on either side of native far.
6. Each HBM puff renders ten quads in the ordinary native tessellator batch. DH copies raw geometry without removing it from that batch. A quad is eligible for replay if any corner is beyond saved native far (`clipZ > clipW`). Near geometry is left to native drawing.
7. At most two eligible quads per particle compete for the shared 128/default, 512/hard-cap stable reservoir. Copies are frame-local and sorted back to front.
8. After Forge world-last handlers, replay uses private VAO/VBO and production shaders. Fragment rejection includes native clip ownership, DH clip bounds, DH terrain depth and source alpha threshold; main-framebuffer native depth is also tested. Replay never writes scene depth. Copies clear after this pass.

Ownership at the native clip boundary is geometry ownership, not emitter ownership. X16 preserves the native emitter throughout. Replay discards its near fragments; native drawing must supply those fragments. Particle births can still be trimmed before that rendering split.

## What the existing evidence establishes

- Supplied 02:41:54/02:41:56 images have identical observer coordinates and a substantial changing discontinuity. The 02:52:24 image also shows separated smoke sections. Counters do not override those pixels.
- Last 02:52 history crosses 305m → 259m → 228m → 197m with continuing relay receipts/births. At 259m it reports 452 native-near quads, 128 captured/submitted and 51 budget rejections; at 197m these are 1511, 128 and 34. This is X15 history.
- The shared reservoir saturates during this flight, proving some eligible candidates are rejected. It does not establish a missing spatial interval, nor explain native-side loss.
- Native-near geometry and relay delivery are present in the recorded transition. They do not establish successful native rasterization, visible replay fragments, or source coverage at each missing position.
- The supplied same-position effects-off result and stationary probes are useful controls, but the stationary probes do not exercise a moving source, relay trimming or the clip-boundary transition.

## Diagnostic blind spots discovered in code

- `native duplicates skipped` increments only for complete trimming. Partial trimming changes sample.x0/y0/z0 silently. A zero counter does not exclude loss through partial coverage trimming.
- `dropped` does not include incoming queue eviction: the receiver silently polls the oldest batch when offer fails. Zero drops is not proof of zero queue loss.
- `budget drops` counts reservoir admission rejection, not later replacement of previously admitted entries, nor all quads omitted by the two-per-particle cutoff. It is not total lost geometry.
- `native near quads` counts quads classified as not-far before the per-particle loop terminates. The early two-far-quad break can leave remaining quads unexamined. It is neither all near geometry nor a visible-pixel count.
- `rendered` increments before glDrawArrays. It is submitted geometry, not passed samples or visible pixels.
- Histories aggregate all HBM streams and particles, without remote/native provenance, per-flight source positions, particle ages or spatial bins. They cannot map the missing screen interval to a pipeline stage.
- `coveredFraction` measures perpendicular distance to an infinite line, then clamps projected u. A fresh marker beyond the segment can produce full suppression. A point marker does not itself prove continuous emission across the suppressed path. This is a concrete hypothesis to measure, not a proven cause here.

## Next isolated experiment

First establish a genuinely loaded X16 session using the existing artifact, with no new code/config changes. The identifying log must contain `NTM native emitter=preserved`; the current process must have exited before the new startup. Reconnecting to the server alone does not reload DH classes. During this investigation a new user-driven startup appeared at 02:58. Its log explicitly identifies metadata `3.3.4_X16`, with three capture sites and two culling gates. X16 startup is independently verified. Its 02:59:32 automatic flight history contains `NTM native emitter=preserved`, continuing receipts and births through 274m, 228m and 197m; these snapshots show 707, 659 and 586 reservoir rejections respectively, with 128 selected/submitted quads throughout. The user subsequently confirmed the gap persists. Earlier flight evidence rotated into `logs/fml-client-2.log`. No agent-launched game or restart was involved.

If that verified session still fails, prepare diagnostics without changing emission, lifetime or budgets:

- Attach bounded provenance to reviewed HBM births: flight entityId, remote/native, sample tick/segment, birth position and ordinary age. Clear world state and bound retention.
- Record input, full/partial trimming (including projected u and marker age/position), queue evictions, successful factory births, live particles and render dispatch in fixed camera-depth bins around the boundary. Include effective loaded build identity.
- Record all source-quad classification counts before selection, selected/replaced/rejected counts per bin, actual native and saved full matrices, source alpha/blend/texture and replay GL state.
- Measure passing replay samples with bounded asynchronous occlusion queries, grouped by depth bins, without blocking glGetQueryObject result reads. Queries prove passed samples, not perceived colour/contrast. Native-batch attribution needs its own scoped diagnostic; do not label append counts as native pixels.
- Add a deterministic moving cosmetic probe that sweeps through native far, using original factories/lifetimes and automatic history. Keep the stationary probe as a control. It tests rendering/boundary/aging but deliberately does not establish server handover correctness.

Choose the A/B only after this locates the first missing stage: coverage trimming off if births are missing; fixed admitted geometry if selection loses a spatial bin; isolated depth/alpha rejection diagnostics if admitted geometry yields no passed samples. Change one mechanism at a time and retain terrain-occlusion correctness as an acceptance requirement.

Do not raise budgets, extend lifetimes, disable depth, restore cancellation, patch Crucible or change weapon mods as an assumed remedy. A passing build or synthetic GL test cannot establish in-game visual acceptance. Dedicated-server and integrated-singleplayer acceptance remain outstanding.


## X17 diagnostic implementation

`NtmTrace` records camera-depth bins (<128, 128–192, 192–256, 256–320, 320–512, >=512m; unavailable separately). Each bin includes cumulative received/full-trim/partial-trim counts, successful addEffect-tail births by native/relay/unattributed origin, a sample of the actual active EffectRenderer lists every five particle-update ticks, actual ParticleRocketFlame age ranges, per-frame dispatches, all classified source quads, and final reservoir entries. Geometry counters explicitly do not represent passed pixels.

Provenance uses at most 4096 weak particle keys; active-list sampling examines at most 16384 entries per scan. The existing 24-entry automatic history prints the bounded trace. Queue evictions are counted separately across all relay batches. Geometry and live-list counts are sampled at different render/update times and can differ slightly; cumulative birth/input binning uses the camera at that event, not a future observer pose. Age ranges are available for the exact reviewed ParticleRocketFlame type; other HBM types report unavailable age.

No emitter, particle-update, coverage algorithm, selection rank/budget, shader or protocol behavior changes. The additional geometry observation inspects every source quad before the existing two-quad early stop, allowing its omissions to be distinguished from reservoir selection. Final selected counts include replacements correctly. No GPU pixel claim is made. No moving probe or depth bypass is included in this first diagnostic; the existing automatic real-flight history exercises the actual relay/handover.

Validation before packaging: compilation and 72 core checks passed, including depth-bin boundaries and unavailable-camera classification. The next run must identify X17 and `NTM stages v1`; no precisely timed screenshot is required. Root cause and in-game correctness remain unproven.


## Verified X17 budget A/B and NTM Next reference

The 03:13:22 flight history independently confirms 512 submitted quads throughout the transition, versus 128 in the baseline. At tick 9090 (last relay endpoint 220m), the 256–320m bin retained 93 native and 22 relay quads; the 192–256m bin appended 753 native and 220 relay near quads. User reported the exact same gap. At 03:13:23 the temporary in-memory budget restored to 128 after 228 received HBM samples and 40 quiet client ticks. No config was saved. This weakens the shared reservoir budget as the principal explanation; it does not test the separate two-quads-per-particle limit.

A read-only client-thread inspector queried the real GPU uniforms without retransformation or replacement of classes: samplers 0/1/2, lightmap enabled, alpha cutoff 0.003921569, reverse depth enabled, and native/extended matrices matching the CPU matrices. This was a post-flight state snapshot, not a measurement of visible smoke pixels during the gap.

The user supplied https://github.com/Warfactory-Official/ntm-next . Source inspected at commit `d49dbbb21b4d3fe57f43d0bf634f3fcc77966b03` (master at inspection). The initial recursive API tree was truncated; no absence claim is based on that tree. Targeted source files were fetched directly.

Verified reference mechanisms:

- `fabric/src/main/java/com/hbm/client/FarView.java` uses Crankshaft/Flywheel `LodTerrain` to report LOD distance to the server.
- `handler/FarTracking.java` caps the requested range with server configuration. `mixin/MixinTrackedEntity.java` expands tracking for entities marked `FarVisible` and permits their delivery when the chunk is not normally tracked by that player.
- `entity/missile/EntityMissileBaseNT.java` implements `FarVisible`; its client tick emits its real contrail from the interpolated movement step. `mixin/MixinLevelExtractor.java` exempts marked entities from compiled-section visibility rejection.
- `client/particle/RocketFlameVisuals.java` creates and ages independent effect records while the visualization backend is supported, removing expired or wrong-world effects. `RocketFlameVisual.java` supplies all ten billboard instances to that backend; its material uses color-only writes and order-independent additive transparency. These visual choices are not automatically faithful to the 1.7.10 alpha-blended source.
- Build uses `dev.engine_room:crankshaft-fabric:1.6.0+mc26.2`. Its published sources archive contains actual `DistantHorizonsCompat`, `LodDepthPass` and `DistantHorizonsFade` implementations. Archive URL: https://repo.warfactory.co/releases/dev/engine_room/crankshaft-fabric/1.6.0+mc26.2/crankshaft-fabric-1.6.0+mc26.2-sources.jar ; SHA256 `3197fa50d5c443aa3f361e2c1970704006aeffe3d1c99b1cdb82b7bea05f34ff`.
- That compatibility reprojects DH depth into main depth and reconstructs the depth input used by DH's vanilla fade, distinguishing terrain and non-terrain foreground content. Its own code explicitly documents that the fade can overwrite non-terrain draws. This is broader than increasing emission/tracking range.

Implication: a fresh NTM-specific design is reasonable, but copying Next's tracking mixins into Crucible or adding its modern renderer dependency would violate this task's constraints. Keep the DH-owned observed relay, optional adapters, smokeless exclusions, source lifetimes and bounds. Reconsider particle drawing as a coherent NTM path across the near/far region, with explicit scene/LOD depth and compositing order, rather than assuming the native-far classification proves that the native pixels survive later passes. Preserve the working MCH path and current NTM implementation as a comparison; do not delete the feature wholesale.

A newly identified rendering-order hypothesis deserves a controlled check before a rewrite: vanilla 1.7.10 renders particles before translucent terrain, and this DH integration invokes `renderFade(true)` after the translucent terrain pass. `distant_smoke.frag` discards every near fragment on the assumption native drawing owns it. `vanilla_fade.frag` bases replacement on scene depth behind translucent content; near smoke contributes color but no depth. Therefore native smoke could be drawn and subsequently composited away, leaving replay unable to replace it. The actual effect depends on background depth, fade state and Angelica's active path; this is not yet proven to cause the user's gap. Crankshaft's explicit fade/depth handling makes this a more specific reference-backed hypothesis than emitter cancellation or more budget changes.

No rewrite or new client jar was produced during the upstream comparison.


## Positive fade A/B and X18 correction

03:21:42: in-memory fade A/B changed `DOUBLE_PASS` → `NONE` on the running X17
client. The user reported no trail gap. Flight history flushed at 03:24:31;
03:24:32 confirms restoration to `DOUBLE_PASS` after 228 HBM samples and 40 quiet
ticks. No emission, lifetime, quad-budget or particle-rendering changes were
included in that test. This is a positive causal isolation of the fade path,
though it does not alone prove exactly which fade operation loses every pixel.

The X18 implementation moves only the inspected rocket-flame near geometry
past the late fade. It copies source geometry once, withdraws the exact native
append only after complete near admission, and replays near fragments using
native projection/depth and source appearance. It does not duplicate near
blending or disable terrain fading. Native near geometry has a separate bounded
capacity matching the existing vanilla particle-layer ceiling; the distant
quad budget and selection are unchanged. Tests cover near/far ownership,
foreground terrain, close projection and source eligibility. See the X18
section in DISTANT_EFFECTS.md for bounds/fallbacks and remaining acceptance.

The X17 source baseline is preserved locally under `/tmp/dh-x17-baseline` and
the X17 packaged artifact is unchanged. No NTM Next/Crankshaft source was copied.
