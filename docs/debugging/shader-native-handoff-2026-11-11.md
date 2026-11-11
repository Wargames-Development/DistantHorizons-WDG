# Shader native/LOD actor handoff — X71

User report: player LODs appear doubled/trailing in the ordinary main view with shaders enabled. Whether scope use is required to trigger the report has not been established. A complete multiplayer visual reproduction is still required.

## Confirmed defect and bounded correction

`ActorRenderer` previously identified native rendering using the framebuffer object captured before DH setup. Angelica 2.2.14's `RenderTargets.createGbufferFramebuffer` creates separate framebuffer objects for shader passes and attaches the same `currentDepthTexture` to each. Thus a native entity pass can fail the ID check even though it belongs to the primary scene. Its native player/vehicle draw is then not recorded, leaving the corresponding actor eligible for distant submission.

X71 retains the same-FBO fast path and additionally recognizes an equal, nonzero depth attachment object and attachment type. This is used consistently in player pre/post events and vehicle primary-view ownership. Texture and renderbuffer object names cannot match across their namespaces; missing depth does not establish shared ownership. Existing shadow, auxiliary-view, drawing, local-cockpit and server-recipient guards remain. The first accepted shader framebuffer alias is logged once per connection as `DH native actor handoff: shader FBOs ... share scene depth ...`.

No shader pipeline selection, frame history, projection, packets or saved-data formats are changed by this follow-up. X70's previously built actor-jitter and laser work is retained.

## Validation and artifacts

- `./gradlew :dh:forge17:checkDistantEffects collectJars --offline --console=plain`: passed; 215,654 focused assertions; production remapping/packaging passed. Successful packaging advances the existing counter from X70 to X71.
- `scripts/tests/actor_model_gl_test.c`: 125 real OpenGL checks passed, including separate shader-phase framebuffers sharing native depth and an independent private-camera depth attachment.
- Jar CRC passed. Comparing X70 to X71 shows only `ActorRenderer` and its three compiler-generated/nested classes changed among class files. No classes were removed or added.
- Artifact: `build/libs/DistantHorizons-3.3.4_X71.jar`.
- SHA-256: `5f8cb0c9a3773346823c7511bb39c6ee6edfc72064921652b45ac7b8c50397e2`.

Companion scope-terrain correction: FMUR `1.8.06_X206-pip-scope-test`, using existing Angelica `2.2.14_X4`. No Angelica source change is needed for these two corrections. Neither new jar was installed, committed or published.

## In-game acceptance

Use a fresh launch with shaders enabled. Observe a moving and stationary player crossing the native/LOD boundary before opening a scope; also test an MCHELI vehicle. Check normal view, Angelica zoom, and ordinary Flans PiP open/close. Confirm there is one model, no offset/trailing copy, and no persistent change after closing the scope or toggling shaders. Check the handoff log and existing actor diagnostic native/distant counts if the symptom remains. Automated fixtures establish the corrected ownership rule and pixel/depth behavior; they do not prove this is the only cause of the reported temporal ghost.
