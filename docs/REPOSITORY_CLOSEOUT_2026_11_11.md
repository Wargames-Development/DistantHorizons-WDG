# Repository closeout assessment — 11 November 2026

This supplements the earlier checkpoints with the user's latest two-player report and checks run against the current checkouts. Historical handoffs describe earlier candidates; their open jitter/laser reports do not override the newer acceptance below. This is a readiness assessment, not full release acceptance.

## Accepted behavior and bounded commits

The user reports that MCHELI laser targeting now works, and moving MCHELI vehicles and players no longer double or trail under shaders. Retain the current DH native-depth ownership and stable-colour jitter corrections. EFR's optional native selection-box seam and MCHELI's explicit dry-grass identities are the paired laser corrections. Unsupported neighbour/tile-dependent distant shapes still return unknown.

EFR `master`: `08612210`, native model selection boxes, committed and pushed through GitHub Desktop. Current metadata stays `3.6.0_X10`.

MCHELI `feature/dh-lods`: `f9e0518`, native dry-grass policy and regression coverage, committed and pushed through GitHub Desktop. Desktop accepted the already resolved `gradle.properties` conflict. The checkout's newer `1.9.01_X212` counter and `branch=dev` are preserved rather than reset to the X206 handoff. This does not establish acceptance of all intervening work or promote the complete branch to main.

DH `feature/mchr-fmur-player-lods`: `ae3a36778`, laser, actor ownership and jitter corrections plus this readiness record, committed and pushed through GitHub Desktop. Metadata stays `3.3.4_X71`. Full release promotion remains gated by the open behavior below.

## Open work before full integration is called complete

1. **Main-view actor lighting:** `ActorRenderer.drawModel` captures with program 0, the vanilla lightmap and standard item lighting. The compositor preserves that captured colour and the established material-254 temporal bypass. Shader depth/cloud integration does not supply native shader material/shadow parity. The user's darker tank transition is an unresolved presentation issue; no measured performance tradeoff justifies dismissing it.
2. **Scope lighting:** the Angelica scope shaders process native geometry before FMUR's `ShaderScopeScene.drawForward` adds distant terrain and actors. That layer retains its existing lightmap/depth encoding after shader-pack processing. This explains a structural source of the lighting discontinuity, not a complete diagnosis of every visible colour/fog difference. Matching the shader appearance requires a separate render-contract change and visual acceptance.
3. **WGQOL weather:** FMUR's `LightweightCamera` and `ShaderScopeScene` call `CameraWeather.render`, which draws its own vanilla-style precipitation. WGQOL instead owns a separate `WeatherEffects` renderer and particle collection. Neither scope path calls that renderer, so WGQOL mist/sand/rain parity is not implemented. Replaying Minecraft's ordinary effect renderer does not replay this private collection. A future integration must render the existing population without ticking/spawning a second population or corrupting primary shader state.
4. **Unshaded scope sky:** `LightweightCamera` clears to sky colour and draws the sun. It has no moon, star or custom-provider sky pass. The shader scope invokes `renderSky`, explaining the differing coverage. The user also reports the sun absent without shaders; the existing sun pass means that particular failure still needs runtime diagnosis rather than being declared wholly unimplemented.
5. **Fog identity:** the screenshot alone cannot establish which shader atmospheric term is present. WGQOL ground mist is local custom geometry; shader-pack atmosphere is a separate path. Compare the same scene/time/weather with WGQOL mist toggled and then shader atmosphere toggled, recording the pack/options and scope/native/LOD boundaries. No source or screenshot evidence currently establishes that these are the same fog.
6. **Migration completion:** prior DH/MCHELI/WGMap handoffs retain legacy WGMap occlusion APIs/fallbacks and explicitly do not establish complete detached MCHELI missile-body support. Existing smoke/explosion relays and accepted vehicle rendering are not proof of a complete replacement. Inventory remaining consumers, missile-body tracking/rendering and explosion coverage before removing legacy APIs or promoting the whole migration.
7. **FMUR branches — ancestry checked:** GitHub Desktop's History comparison shows `barrack-AngelicaAPI` is **behind 0 / ahead 1** relative to `feature/dh-lods`. The scope branch already contains that DH baseline; no merge is needed to recover it. Its one extra committed change is `b8c9f1d` (First API Attempt), which also touches WGCore integration/dependencies and explosion code. The pending scope patch has 12 files and remains uncommitted pending complete review and runtime acceptance. Desktop shows the committed API change advances the actual branch counter from X200 to X204; the pending scope work advances it to X206. The older handoff's X209 artifact is historical packaging evidence, not a reason to reset or manually override the current checkout's version rules.
8. **Angelica acceptance:** current metadata is X6, not the older X4 pair. The handoff documents a reproduced window draw-buffer failure and correction, with fresh-launch X6 scope/reload acceptance still outstanding. The user's latest successful moving-actor report does not by itself establish every X6 lifecycle case or Windows parity.

WorldBorder is excluded from commits, pushes and merges by explicit user instruction. Its existing working files are untouched.

## Verification in this assessment

- EFR: `./gradlew compileJava --offline --console=plain` passed; tasks were up to date.
- MCHELI: Java 8 `./gradlew test --offline --console=plain` passed; cached suite reports contain 249 tests, zero failures/errors and two skips. No claim of a new in-game run.
- DH: `./gradlew :dh:forge17:checkDistantEffects :dh:core:test --offline --console=plain` passed. Effects checks executed: 215,654 assertions. Core test task was up to date.
- DH actor fixture: compiled the existing CGL fixture with clang and ran it against the production `actor_model.vert`/`.frag`; 125 OpenGL checks passed, including shared-depth ownership and eight jitter phases.
- FMUR: Java 8 compile/test plus `auxiliaryViewChecks playerLodChecks externalPlayerPresentationChecks playerLodServerChecks` with `tools/player_lod_checks.gradle` passed: 35 auxiliary, 10,110 LOD and 2,030 presentation assertions plus server/data boundary checks. The ordinary Gradle test task has no test sources.
- FMUR: `python3 tools/render_checks/check_scope_terrain_gl.py` passed 155 OpenGL checks against production shader strings. Graphics access required leaving the filesystem sandbox; the initial sandbox attempt could not create a context.
- Angelica: `./gradlew test --offline --console=plain` passed, including its configured GL tasks; test tasks were up to date. Current root unit reports contain 577 tests and no failures/errors/skips. Historical X6 GL results and remaining runtime requirements are in `docs/SECONDARY_CAMERA_2_2_14.md` in the Angelica repository.

No package task, version increment, client/server deployment, shader-option change, world/database change or live multiplayer test was performed during this assessment. Git review, commits and pushes after the user's workflow instruction use GitHub Desktop exclusively.

## Final disposition of unfinished checkouts

Angelica X6 and FMUR X206 source changes remain in their original working checkouts and branches. Their current automated tasks pass, but a complete diff review and the outstanding scope lifecycle, lighting, weather and sky acceptance are not complete. They are not promoted to master/main or described as finished. Neither branch was switched, merged, reset or stashed. WorldBorder remains untouched. EFR is the only default-branch promotion in this pass; MCHELI and DH are published feature checkpoints.
