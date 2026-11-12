# Vehicle shader lighting candidate — 12 November 2026

Status: implemented, built and automatically checked; **user accepted the lighting result in-game on 12 November 2026 and authorized main/default-branch merges**. The results below record the original candidate validation; they do not claim each manual checklist item was individually tested. Use all three candidate jars together. Keep the accepted MCHELI, WGMap, EFR and other companion versions/configuration; retain the existing actor/MCHELI experimental launch flags and server ownership policy.

## Matched test artifacts

Folder: `build/vehicle-shader-lighting-test/` in DistantHorizons-WDG. It includes the three runtime jars, `SHA256SUMS.txt`, validation logs and the temporary fixture sources used below. Do not install the `dev`, `api`, `predowngrade` or `sources` jars.

| Runtime artifact | SHA-256 |
| --- | --- |
| DistantHorizons-3.3.4_X73.jar | d29f0f16430d0050f4c85abe3a646142ecefdf30127be2bf71f4519694fb7b4c |
| angelica-2.2.14_X10.jar | bc1b24c18f0b6e320479d06ce975bae67457f4d687a41f4e7013399bc60e49bf |
| FMUR-WDG-Edition-1.8.06_X211.jar | 6475e5f7e1c4769e034708b8f226a2056a4d940c22f258d4cc0f025188bbbfd5 |

All runtime class entries outside multi-release directories were inspected: none require newer than Java 8. Counters advanced only through successful normal packaging. DH X72 and Angelica X8/X9 were intermediate successful builds, superseded during review; the table is the final set. FMUR's current metadata produces X211 without the historical `pip-scope-test` suffix.

## Implementation and submission contract

Angelica's additive `EntityGeometry` v1 API borrows the enclosing primary/private scene's existing entity program and read-only samplers. A render-thread capture context routes `Pass.use`, pass matching and rebinding callbacks from either pipeline instance to the caller-owned FBO. No public pipeline is swapped and no world/sky/deferred/composite/shadow lifecycle is run per model. Nested captures still reject entry. Shader storage/custom-image packs and programs whose first output is not scene colour are rejected by this path.

The capture keeps the enclosing `RenderingState`, frame counter, matrices/history suppliers and pipeline selection intact. It temporarily uploads the local near/far projection and inverse to the entity program, restores them afterward, suspends/restores raster override bookkeeping, clears stale held-item/entity overlay attribution, and reconnects the caller's dynamic uniform listeners on return. RGB/alpha accumulate as premultiplied colour/coverage. Extra MRT outputs are discarded; DH retains its established material-254 temporal marker and non-glossy normal contract.

DH's model capture and shared actor scene use RGBA16F so night values and HDR block-light values survive to the enclosing pack presentation stage. Main actors retain their existing pre-finalization composition/depth/occlusion path.

Colour scopes negotiate additive target/projection APIs and submit actors before private shader finalization. The scope supplies private scene colour, optional known-pack material/normal attachments, full/opaque DH depth, native depth, dimensions and opaque/no-hand native depth. DH samples a copied immutable DH terrain depth, writes full/opaque actor depth, and writes opaque native depth separately without sampling the attached native image. This preserves near-vehicle ordering and fog distance even in front of DH's near plane. Transparent parts leave opaque depth untouched. Native scene depth also protects later actors from earlier opaque actors. The scope's forward callback retains accepted terrain/coverage behavior and skips the already-submitted actor replay.

Active FLIR and shader-off captures keep their existing lighting/geometry path. Gameplay, authority, packets, saves and content definitions are unchanged. MCHELI/WGMap/WGQOL/WGCore/WorldBorder source was not edited. Shader-lit shared actor captures also cover detached player models using the same capture mechanism; include them in regression testing.

## Validation run

Java 8 home: `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home`. DH/Angelica Gradle launcher: `/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`; their normal Java 8 artifact pipeline was retained. Gradle cache/GL access used approved sandbox escalation.

| Check | Command/result |
| --- | --- |
| DH | `JAVA_HOME=<JDK25> ./gradlew --offline :dh:forge17:checkDistantEffects build` — PASS, 215654 core assertions and production packaging |
| Angelica | `JAVA_HOME=<JDK25> ./gradlew --offline -I /tmp/vehicle-pack-check.gradle :test :glCoreTest :build` — PASS; 578 ordinary tests; 28 GL cases, 22 passed and six existing platform skips; style/build tasks passed |
| Local pack | The Angelica command compiled actual BountifulStars entity, DH terrain and water sources, with the shipped `defineIsIris=true` configuration default initialized in the fixture |
| Capture GL regression | New Angelica test checks HDR/coverage, projection/inverse restoration across three local projections, rebinds through primary/private pipeline instances, inherited raster locks, entity/item attribution restoration and an injected renderer failure; caller pixels/matrix state remain intact |
| DH production compositor | `clang -Wno-deprecated-declarations scripts/tests/actor_model_gl_test.c -framework OpenGL -o /tmp/vehicle-actor-gl`; `/tmp/vehicle-actor-gl dh/forge17/src/main/resources/assets/distanthorizons/shaders/actor_model.vert dh/forge17/src/main/resources/assets/distanthorizons/shaders/actor_model.frag` — PASS, 131 pixel/depth/ownership/alpha/cloud/jitter/HDR/near-native-depth checks |
| FMUR core | `JAVA_HOME=<Java8> bash ./gradlew --offline -I tools/player_lod_checks.gradle playerLodChecks externalPlayerPresentationChecks playerLodServerChecks` — PASS, 10110 and 2030 assertions plus dedicated-server presentation boundary checks |
| FMUR scope/thermal | `JAVA_HOME=<Java8> bash ./gradlew --offline -I tools/player_lod_checks.gradle -I /tmp/fmur-current-particle-check.gradle cameraEnvironmentChecks currentParticleGpuChecks -PlwjglNatives=/tmp/fmur-nvg-natives -PwgqolRuntime=/Users/rhysh/Documents/GitHub/WGQOL/build/classes/java/main` — PASS, 140 environment checks, 15822 thermal profile/admission checks and 303 particle/depth/heat/NVG checks |
| Actual DH companion fog | `JAVA_HOME=<Java8> bash ./gradlew --offline -I tools/player_lod_checks.gradle cameraEnvironmentChecks -PlwjglNatives=/tmp/fmur-nvg-natives -PwgqolRuntime=/Users/rhysh/Documents/GitHub/WGQOL/build/classes/java/main -PdhRuntime=/Users/rhysh/Documents/GitHub/DistantHorizons-WDG/build/libs/DistantHorizons-3.3.4_X73.jar` — PASS, 150 environment/companion checks (the earlier combined invocation subsequently failed its separate legacy GL3 task; see limitations) |
| Scope terrain/fog pixels | `python3 tools/render_checks/check_scope_terrain_gl.py` in FMUR — PASS, 216 production-GLSL checks |
| Shader-off lighting | `JAVA_HOME=<Java8> bash ./gradlew --offline -I tools/player_lod_checks.gradle -I /tmp/vehicle-lighting-only.gradle vehicleLightingOnly` — PASS. Temporary harness invokes the unchanged existing `verifyLighting` and `verifyActorBrightness` assertions using an explicit RGBA8 EXT framebuffer: daylight/night/block light, shader residue, lightmap alpha, scaled normals and unloaded-actor brightness |
| FMUR packaging | `JAVA_HOME=<Java8> bash ./gradlew --offline build` — PASS, X211 |

The local BountifulStars source snapshot was recreated in `/tmp/vehicle-bountiful/shaders` from FMUR's existing `BountifulStarsALPHA0.0.3.zip`. Its entity GLSL hash matches the installed WDG water-input-probe pack. No shaderpack files or live game settings were edited. The full live pack option combination was not rendered automatically.

### Failures and limits, retained for reproducibility

- Initial Gradle calls failed on sandboxed cache-lock access; FMUR's wrapper requires `bash ./gradlew` because it is non-executable. Approved cache access resolved this.
- Angelica first compilation exposed a missing qualified shadow-state name; corrected. A subsequent build failed Checkstyle on two wildcard imports in the new test; corrected. Failed builds did not consume counters.
- The initial capture test found uninitialized `RenderingState` fixture buffers; initialize them as real world rendering does. The local pack compile initially failed because configuration annotations are not applied by the unit bootstrap, leaving `defineIsIris=false` instead of its shipped true default; the fixture now initializes/restores that default. No production macros were changed.
- An intermediate DH compile caught a constructor removed during a local refactor; restored and the final core/build checks passed.
- Passing only DH's loader class directory to FMUR's companion fog test omitted API classes. Retesting against the complete X73 jar passed.
- The optional unmodified `playerLodGpuChecks` task requires GL 3.0 compatibility while using legacy fixed-function rendering. This macOS legacy Pbuffer advertises GL 2.1, so that complete legacy task **did not pass** here. Invoking its lighting subset against its default Pbuffer also failed with `GL_INVALID_FRAMEBUFFER_OPERATION`; using an explicit RGBA8 EXT framebuffer fixed the fixture and all unchanged lighting assertions passed. The separate core-profile production actor compositor suite passed 131 checks. Rerun the complete legacy task on a machine with GL3 compatibility if that additional platform coverage is required. Do not report that task as passed or silently skip its assertion.
- Component tests establish capture/submission contracts, not native/LOD visual parity in Minecraft. No live gameplay/client run or user acceptance occurred.

## In-game acceptance checklist

1. With the same shaderpack/options as the accepted baseline, compare one vehicle across the native/LOD threshold in daylight, deep night and local block light. Approach and retreat; check exposed and shadowed faces. Capture both sides at the same time/weather.
2. Repeat through ordinary colour PiP and FLIR-off colour optics. Include both near vehicles (inside DH's near plane) and distant vehicles, foreground terrain/solid cover, and overlapping vehicles.
3. Toggle active FLIR on/off repeatedly: thermal remains lightweight, its intentional atmosphere bypass survives, and colour mode restores shader lighting/fog/sky/rain.
4. Move, rotate and zoom while the vehicle crosses the ownership boundary. Check for jitter, duplicates, missing frames, depth popping and changed motion trails.
5. Check glass/canopy, rotor/propeller edges, transparent parts and a vehicle behind another. Glass must retain colour/coverage without becoming an opaque depth blocker.
6. Recheck accepted DH/native fog, sky and rain in colour scopes. Close/reopen/resize the scope, reload the shaderpack and reconnect; the primary view must remain intact.
7. Disable shaders and repeat the transition and colour/thermal toggles. Retain the accepted shader-off appearance. Include one detached player as a shared-capture regression check.
8. Review the client log for capture/isolation failures. Accept or reject the matched set; **do not merge to main until user acceptance**.

Rollback: restore the previous accepted runtime jars as a matched set and keep the fix branches. No content/save/protocol migration is involved.
