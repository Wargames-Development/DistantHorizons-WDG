# DH integration handoff — 11 November 2026

## Current outcome: incomplete shader and zoom work

The user is moving the investigation to a new chat and explicitly requested that the existing work be committed on its branches. These are reproducible checkpoints, not a release or a claim that all rendering is correct.

**Latest user report on DH X68 / MCHELI X205 / FMUR X209 / WGMap X165:** unzoomed operation is smooth enough. Angelica zoom still causes designation trouble; zooming and then using the laser does not work reliably with shaders. Vehicle model jitter under shaders also occurs while standing still. Earlier reports linked it to camera/vehicle movement, but stationary reproduction supersedes that limitation. The latest jitter correction is therefore NOT runtime-accepted. Designation on arbitrary valid surfaces within the Z-10W CM502KG's 550 m range must be made reliable before any missile launching. The user does not want missile-watch commands or firing tests yet.

Do not restart the investigation by declaring SSAO, paired depth or jitter offset solved. Those changes address tested defects but have not explained the whole runtime failure.

## Branches and committed companions

| Repository | Branch | Commit already made during this handoff |
|---|---|---|
| FMUR | `feature/dh-lods` | `e80debe` — private colour/heat actor camera integration and isolation; working tree clean |
| MCHELI | `feature/dh-lods` | `b0ab8cc` — DH-first laser adapter, BVR/GPS/designation graphics; working tree clean |
| WGMap | `feature/migrate-lods` | `ba99440` — physical waypoint beams and terrain-occlusion setting; working tree clean |
| DH terrain | `feature/mchr-fmur-player-lods` | `e9032bc4c` — separate tree/material, EFR path colour and SSAO-boundary fixes |

The rest of the DH actor/ray/shader implementation and this handoff are committed together as the subsequent checkpoint on the same DH branch. No branch was pushed, merged or released. Original WGMap main remains at `568650b`; its duplicate dirty edits were verified to exist on the migration branch and backed up at `/Users/rhysh/Documents/GitHub/WGMap/.git/codex-backups/wgmap-main-before-migrate-lods-2026-11-11.patch` before cleaning that checkout.

FMUR's collaborator should start from `feature/dh-lods` / `e80debe` and read its `docs/DH_LOD_HANDOFF_2026_11_11.md`. Preserve the optional ActorCameraApi v1 and ThermalRenderApi geometry-isolation contracts unless deliberately coordinating changes on both sides.

WorldBorder has unrelated uncommitted DH work. The user explicitly instructed: **leave WorldBorder untouched**. Other checked companion mod repositories (EFR, HBM, lwjgl3ify, WGQOL, WGCore, ReplayMod, WGEvents, WGHangers, foliage-plus, hitmarkerplus, ItemControl and BetterBuildersWands) were clean.

## User-confirmed behaviour and limits

- Earlier X57 scene: vehicle visibility/terrain occlusion to 750 blocks; movement, FOV/zoom and rapid vehicle/native transitions behaved well. This is historical acceptance, not a guarantee that every later shader/private-view change preserves it.
- First-person Abrams cockpit terrain-over-model and Ka52 shake were fixed on the earlier build; cape rendering is off by default; player torso orientation handoff was confirmed fixed.
- Player guns and NVGs appear on the LOD. Vehicle animation and explosion visuals were observed working. Vehicle thermal silhouettes were confirmed with and without shaders. Player thermals and every PiP depth/occlusion scenario are not comprehensively accepted.
- EFR dirt-path side colour and the oak-log/leaves terrain issue were confirmed corrected. Exact ChickenChunks/TESR partial geometry remains open. Tiny vehicle vertical changes at native/LOD handoff remain a lower-priority issue.
- Shader model lighting/material/shadow parity remains open. Flans third-person ADS animation is deferred.

## Implementation map for the next investigation

DH `ActorRenderer` captures native and DH camera origins/matrices, controls native/LOD ownership and local-cockpit exclusion, draws isolated model colour/self-depth, and composites against copied terrain/native depth. `ModelCapture`, `ModelScene`, `NativeSceneDepthCopy`, `ShaderEffectsTarget`, `SamplerBindings` and `actor_model.frag` implement the GL path. `ShaderActorJitter` reads the compiled supported pack's DH solid program, active framemod8 and view size to align its known eight-frame jitter pattern. It logs detected strength once. Unsupported patterns use zero, not a guessed offset. **Inspect actual runtime logs to verify whether the intended offset is detected and matches the scene being sampled.**

X65 isolated shader model rendering and sampler bindings; X66 replaced inherited terrain normal/fresnel under actor pixels; X67 synchronized solid actor pixels into the live/opaque DH depth pair using immutable terrain input; X68 added compiled-pattern jitter alignment. The user still sees jitter. Avoid adding more speculative passes without measuring which state/depth/colour changes while the world and vehicle are stationary.

`ActorCameraApi` / `ActorCameraPass` supply private PiP/thermal actors. FMUR `DistantActorCamera`, `DistantCameraGeometry`, `LightweightCamera`, `ThermalCamera`, `ThermalScopeEffect` and `ThermalRenderApi.beginIsolatedGeometry()` own the companion private-view boundary. Private renders must restore primary Iris selection, matrix/FOV caches and samplers.

DH `DhTerrainRayApi` / `TerrainRayWalk` read finest stored DH data without loading/generating world chunks. Cache: 32 sections; at most 4 requests and 1 adoption per 50 ms; refresh starts after 1 s and old snapshot validity ends after 2 s. X68 handles path/farmland heights, slab halves, snow layers and all eight vanilla stair ray shapes. Unsupported custom/tile-dependent shapes and unavailable finest columns remain unknown. An initially completed in-memory read can now be adopted without a mandatory unknown frame. No saved-data format or packet layout changed.

Unknown-ray diagnostics are automatic, at most 20 messages per world and no more than 1/s: 1 initial snapshot pending; 2 missing column/interval; 3 unsupported shape; 4 snapshot expired; 5 request budget; 6 missing/unsupported snapshot. Coordinates identify the failed cell. Up to 16 unsupported names are **candidate states seen in snapshots**, not necessarily the exact failing cell's state. Correlate those messages with marker disappearance. The green marker HUD only appears when MCH_LaserGuidanceSystem.targetConfirmed is true, so determine whether a frame lost confirmation or whether a still-confirmed marker was projected/clipped.

MCHELI `MCH_LaserGuidanceSystem` now uses the current render-view entity position for logic ticks, replacing RenderManager's previous rendered origin. `MCH_RenderLockBox` scales about the captured eye. `MCH_RenderGPSPosition` captures/restores world projection for the overlay; check its interaction with Angelica zoom. `DhLaserTerrainBridge` is the optional common API seam; server `MCH_LaserTargeting` still validates weapon, aim and first obstruction. Do not fix display dropout by accepting untrusted or occluded points. Do not silently replace the existing 1,500 m designation policy solely because this weapon's lock range is 550 m.

WGMap `WaypointDhBeams` optionally hands physical beam geometry to DH. **Terrain hides beams** controls through-terrain versus per-fragment native/DH occlusion. Show Beam is independent. The obsolete showDistantBeams config key/accessors are preserved but no longer gate distance or appear in the UI. EFR world height and captured-eye placement are retained. Legacy WGMap occlusion modules/APIs remain for other consumers; MCHELI prefers DH when installed. Third-person camera changes are owned by another developer; coordinate rather than overwrite.

## Runtime, builds and join pitfalls

Test instance: `/Users/rhysh/Library/Application Support/PrismLauncher/instances/Wargames DH LOD Integration Test/minecraft`. Angelica 2.2.14, lwjgl3ify 3.0.28-development.11+d1361fcca1, shader pack `BountifulStarsALPHA0.0.3-WDG-water-input-probe.zip`.

Currently packaged: `DistantHorizons-3.3.4_X68.jar`, `MCHELI-R-WDG-Edition-1.9.01_X205-dh-lods-test.jar`, `FMUR-WDG-Edition-1.8.06_X209-dh-lods-test.jar`, `WGMap-0.1.2_X165-migrate-lods.jar` in each repository's build/libs. These checkpoint commits did not rebuild jars or increment versions. The MCHELI content installation is an extracted directory whose name need not equal its actual mcmod.info version. Preserve the current name and content while updating only code/metadata; never rename it during a running client.

MCHELI requires exact client/server version strings. A client-only change still requires matching MCHELI code metadata server-side. The prior late missing-class crash came from renaming a live mod directory; a later FML rejection was a version mismatch. Check the new log rather than assuming either cause.

No weapon-content or chunk-loader changes were installed. APKWS has chunk loading off; the Z-10W CM502KG already has it on. Native MCHELI projectile range culling/tracking and the absence of a complete MCHELI DH body proxy remain separate from smoke visibility. Do not resume projectile testing until designation is reliable. No server deployment was performed by the agent. Verify actual running processes before any new client install; do not assume clients are closed based on this handoff.

Last client install backup: instance sibling `dh-lods-test-archive-metadata/before-X68-MCH205-2026-11-11-020247/install-manifest.json`. Prior X67/X204 backups remain. Do not delete DH databases to mask rendering defects.

## Verification immediately before commits

- DH: `./gradlew :dh:forge17:checkDistantEffects :dh:core:test --offline` PASS: 215,383 component checks; 126 core tests / 30 suites.
- Actor CGL fixture: PASS, 108 checks. SSAO CGL fixture: PASS, 54 checks. These do not prove the unresolved in-game shader path.
- MCHELI: `./gradlew test --offline` PASS, 249 tests.
- WGMap: `./gradlew check -PwgcoreDevelopmentClasses=/Users/rhysh/Documents/GitHub/WGCore/build/classes/java/main -I /tmp/wgmap-branch-deps.gradle --offline` PASS, 1,213 tests / 234 suites and aggregate boundaries.
- FMUR: Java compilation/test plus existing opt-in checks PASS: 10,110 packet/projection checks; 10 auxiliary ownership checks; 2,030 external-player checks; dedicated-server/data-classloading checks; 15,822 thermal-profile/admission checks; 295 focused GPU camera-particle/isolation checks. The focused task uses `/tmp/fmur-current-particle-check.gradle`, x86 Temurin Java 8 and `/tmp/fmur-nvg-natives` on this Mac. Full legacy Pbuffer suite is not claimed.
- Diff hygiene PASS. No jars, Gradle caches, logs, crash reports, local dependencies, secrets or runtime worlds are staged. The small JSON test-pack inventory contains mod filenames/hashes and relevant hook names, not the mod jars.

## Suggested opening request in the next chat

Continue the existing DH integration from this handoff and the recorded feature branches. First inspect the latest actual client logs and the shader/zoom camera and designation state. Reproduce or measure stationary shader vehicle jitter and Angelica-zoom green-marker dropout; distinguish targetConfirmed loss from projection loss and shader colour changes from depth/ownership changes. Preserve the already-confirmed terrain, cockpit, thermal and player handoff work. Do not change weapon chunk-loading policy, start missile firing/watch tests, overwrite WorldBorder work or alter the other developer's third-person camera changes.
