# Companion visual terrain API v1

`com.seibel.distanthorizons.forge17.effects.VisualTerrainApi` is optional, client-only
and render-thread-owned. It does not authorize actors or replace server policy.

- `register(kind, DoubleConsumer)` installs the main-view collector for PLAYER=1 or
  VEHICLE=2. Register during client initialization; null removes that collector.
- `ready()` means this thread has a valid current-world terrain frame, not that every
  target has known terrain or that a particular shader configuration will succeed.
- `collecting()` admits submissions only inside a main collection or active private
  actor-camera pass. Companions' ordinary late world-last handlers must not duplicate
  the main shader collector.
- `submit(world, kind, x, y, z, centerY, radius, partial, light, Runnable)` queues geometry
  for this view. Coordinates describe an unscaled physical model. The callback draws
  against the active camera/RenderManager origin under DH's projection, lighting and
  framebuffer capture. It must preserve its GL state, keep current authorized pose data
  valid until the immediate flush, and must not recurse into a camera or world render.
  False means not queued; true does not guarantee visible pixels. Coverage/projection
  admission and native/DH fragment depth tests happen afterward.

At most 256 submissions per view and radius <=256 are accepted. Invalid/world-mismatched
inputs are rejected. Callbacks never run on a worker or survive view completion. No
network/thread ownership is transferred. Main callbacks run before shader finalization,
or in the existing nonshader DH world-last stage. Private color/thermal callers collect
first and invoke ActorCameraApi.renderDistant afterward, balancing begin/end in finally.

ActorCameraApi's additive OWNERSHIP_VERSION=1 exposes ownedKinds(): bit1 player, bit2
vehicle. This reports DH-owned actor streams, independently of passive terrain readiness.
Companions keep their existing owner checks to prevent duplicate relay and legacy draws.

Matching Angelica adds FlansScope.actorTerrainProgram()/actorTerrainSource(). They are
read-only private-view metadata, valid during its scene callback. DH samples uniforms on
that program without changing them; absent metadata rejects the shader actor pass.

Updated FMUR/MCHR use this API when available and do not query WGMap for visual occlusion.
Older or absent APIs retain the legacy path. Once supported, an invocation failure does
not silently switch to another occlusion provider. Unknown terrain remains withheld.
The WGMap docs/visual-terrain-migration.md record contains rollout and validation details.
