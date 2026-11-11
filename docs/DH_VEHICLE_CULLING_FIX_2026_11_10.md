# Vehicle cut-outs under zoom and terrain rebuilds

10 November 2026, DH X56. Implemented, component-tested and installed in the closed **Wargames DH LOD Integration Test** Prism instance. Visual acceptance of X56 in the reported Mi-8/Abrams scene is still pending.

## Runtime evidence

The original client had DH X55, Angelica 2.2.14, lwjgl3ify 3.0.28-development.11+d1361fcca1 and FMUR X206. MCHELI is extracted into a directory named X200, but its external renderer and context classfiles exactly match the packaged X201 companion.

The user's `/dheffects status` at 20:04:01 UTC reported two vehicle records, effective range 750, an active provider and two DH model draws. During the subsequent user-controlled reproduction, a temporary read-only Java sampler recorded repeated active actor-pass states with `drawn=0 unknown=2`, and `drawn=1 unknown=1`, with both vehicle records still present. Observed FOVs included approximately 33 degrees and strong zoom below 10 degrees; vehicle distances were approximately 740 blocks. These are unsynchronised diagnostic samples, not exact frame timing or GPU pixel measurements. They establish a terrain-completeness rejection in a reproduced failing view. They do not establish that every original 109/118-block disappearance has the same cause.

The sampler read camera/actor fields only, did not redefine classes or change world/gameplay state, and ended automatically after two minutes. The game processes were confirmed exited before the jar swap.

## Corrections

The conservative camera/vehicle corridor contains off-screen and behind-camera terrain. Both DH's buffer list and Angelica's native section list are view-culled. Previously, absence of those irrelevant sections could invalidate the whole model. Coverage now excludes only queried boxes strictly outside a side or eye plane of the captured native camera. Native vertical sections are checked separately against the portion of their bounds being queried. Touching/intersecting boxes remain required; the native far plane is deliberately omitted so distant sight corridors still need coverage. The same captured origin and camera matrices used for actor rendering drive this test.

Queued terrain updates previously invalidated otherwise displayed meshes: the native adapter rejected sections with pending updates/build tokens, and DH's actor query rejected dirty sections. A native section now remains usable while its built mesh is still selected for the current view. A dirty DH section remains usable while the same uploaded buffer remains selected and enabled. Coverage provenance is immutable and belongs to that buffer. Unbuilt/disposed native sections and missing/released/disabled/replaced/incomplete DH buffers still withhold actors.

Actual model occlusion remains a per-pixel comparison with the displayed native/DH terrain depth. Placed blocks begin affecting that depth when the replacement mesh is displayed. There is no timer that hides a model during a pending rebuild and no timeout that authorises a genuinely missing terrain area.

The status command now reports bounds/preparation/projection/capture rejection counts, counts native vehicles, and retains a recent coverage-rejection location and camera FOV. The retained detail can be read after releasing Angelica's held zoom key to open chat. No continuous diagnostic logging is enabled.

## Pack inspection

`DH_VEHICLE_TEST_PACK_INVENTORY_2026_11_10.json` inventories and hashes all 44 installed jars and the extracted MCHELI class tree before the fix. It also lists named camera/render mixins found in each jar. This is an inventory and hook scan, not proof that every arbitrary transformer is harmless.

Relevant hooks inspected included Angelica's held/scroll zoom and ActiveRenderInfo matrix capture, DH's actor/camera/depth paths, MCHELI's external model adapter, lwjgl3ify's legacy matrix delegation, CoreTweaks' far-plane hook, and renderer hook inventories for ReplayMod, EFR, GTNHLib and mchr-mixin. Angelica's zoom changes effective FOV, which exposed DH's completeness mistake. The live rejection is in DH's actor coverage gate; no evidence from this reproduction requires changing lwjgl3ify, Angelica, WGMap, or companion mod source.

## Validation and installation

- 121 core tests across 27 suites passed, including a real RenderBufferHandler regression for a dirty displayed mesh, replacement provenance, released/disabled/missing buffers and wrong view/generation.
- 213,912 component assertions passed with compile-time Angelica 2.2.12 and installed Angelica 2.2.14. The actual native coverage query is exercised against both class inventories. Geometry tests cover FOV 70, 36, 35, 10 and 1 degrees, rotated/translated cameras, off-screen sections, distant required terrain and an in-view missing chunk.
- 64 actual actor-shader GPU checks passed, including native/DH depth occlusion and partial terrain coverage.
- Packaging and whitespace checks passed. No wire protocol or gameplay/simulation changes.

Artifact: `build/libs/DistantHorizons-3.3.4_X56.jar`.
SHA-256: `328ab4ea7b4d5cc1c73a7db9632bd896e623f962e3f8df24b676dab7a9bf9f48`.

Installed only in the named test instance after the user closed it. X55 was moved outside its mods directory to `dh-lods-test-archive-metadata/before-X56-2026-11-10/`. The active jar hash was verified and there is exactly one active DH jar. Other instances, companion jars and configs were left intact. A client relaunch and manual scene retest remain necessary; no successful X56 world-rendering claim is made here.

## Follow-up: X57 player movement shifting vehicles

The subsequent movement report exposed another stale-origin path. ActorRenderer.snapshot executes during terrain rendering and was reading RenderManager.viewerPos. The actual Minecraft RenderGlobal class calls cacheActiveRenderInfo later, in renderEntities, and RenderManager updates viewerPos there. The actor snapshot now interpolates the current renderViewEntity at the captured event partial tick. It retains DH's distinct terrain origin and the captured eye/third-person model-view transform, and never writes shared native or particle caches.

X57 adds 303 camera-origin regression assertions; 214,215 total component assertions pass with both Angelica fixtures. The source change is confined to the actor snapshot and its regression test/registration; the X56 coverage corrections are retained. The artifact is `build/libs/DistantHorizons-3.3.4_X57.jar`, SHA-256 `0f599ce94ce5de1ade38592437301cb1fc56ac89d3d2524701df7dde91dd02b9`. Visual acceptance of player movement remains pending. No server update or shader/companion rebuild is required.

After the user confirmed the test client was closed, both test JVM processes were verified exited. X57 was installed in the same test instance, its hash verified, and X56 moved to `dh-lods-test-archive-metadata/before-X57-2026-11-10/`. Exactly one DH jar is active. Client relaunch/manual movement retest remains necessary.
