# Distant weapon effects and full-flight trail delivery

All implementation is in Distant Horizons. No MC Heli, HBM/NTM, FMUR, WGCore or
WGMap source or dependency is changed. The weapon mods remain optional. The old
X3 prototype only extended existing MC Heli smoke; this revision also adds a
DH-owned, explicitly subscribed visual trail channel for dedicated servers and
integrated singleplayer servers.

## Install and enable

For full-flight delivery, install the same new DH jar on the client **and the
server**, replacing the previous DH jar. A client-only installation still extends
supported effects already available locally, but cannot recover emissions that
the server never sends. Older clients are not sent the new packets.

The feature remains **off by default**. Open DH settings → Advanced → Graphics →
Generic Rendering → **Experimental Distant Weapon Effects**, or run:

```text
/dheffects on
/dheffects range 8192
/dheffects debug
/dheffects status
```

The server must allow `server.enableDistantTrailRelay` (default true). Its
`server.distantTrailRelayDistance` defaults to 8192 blocks and caps the client's
requested distance. The client ceiling is 16384 blocks, subject to its configured
range and DH's actual far plane. The existing X3 `enableDistantMcheliSmoke` TOML
key is retained as the master switch, with an updated UI label. Individual
`extendMcheliTrails`, `extendHbmTrails` and `extendFlansTrails` switches default true.
Turning the master switch off stops new relay births and the extra rendering.
Particles already created retain their original natural expiry.

## A repeatable solo test

Look towards clear sky or an unobstructed part of the horizon and run:

```text
/dheffects probe mcheli 400 15
/dheffects probe hbm 400 15
/dheffects probe fmur 400 15
```

Run one at a time. Each makes a stationary, clearly visible cosmetic source 400
blocks along the view for 15 seconds, using that installed mod's particle factory.
It does not fire a weapon or modify the world. `/dheffects stop` stops emission;
remaining puffs expire normally. Probes test the local rendering path, **not the
server relay**. They also work in singleplayer and without a compatible server.

`/dheffects status` reports whether the server channel is absent, awaiting opt-in,
or connected; received trail segments; native duplicate suppression; and dropped
work. Renderer diagnostics show supported render calls, captured/drawn quads,
native camera far plane, budget drops, and the reason for a skipped pass. The
same information goes into the client log; `debug` toggles its F3 display. Probes
log status periodically so a fast screenshot is unnecessary.

Then fire one chunk-loading KH36 towards a GPS point. With both sides updated,
`received` should increase after subscription. The trail should continue past
normal client entity tracking, while its older puffs expire naturally. Repeat
with an MC Heli missile whose definition disables smoke: it must stay smokeless.
For NTM use a standard tiered missile, then a custom xenon missile as the negative
test. For FMUR use a projectile explicitly configured with trail particles and
then one with `trailParticles` disabled. Check hills, coast/boost transitions,
toggle-off behaviour, dimension changes and a small salvo. Singleplayer must also
be smoke-tested before release.

## Source rules and ownership

- MC Heli delivery is attached to its real `spawnExplosionParticle` call, after
  its existing disabled-smoke/start/end/flight conditions decide whether to emit.
- FMUR's server adapter mirrors the inspected `trailParticles`, startup age, VLS
  launch phase, trail style and accelerating boost conditions. It never supplies
  a default particle name to a smokeless definition.
- NTM supports the inspected base, tier-3, tier-4 and custom missile contrail
  implementations, including their nozzle layouts, scale and custom fuel style.
  Xenon remains smokeless. Rideable rockets and unreviewed `spawnContrail`
  overrides are deliberately excluded; no generic replacement exhaust is added.

The server publishes observed past segments, not a client simulation of a
missile. It never loads chunks, changes damage/guidance, extends vanilla entity
tracking, or makes a non-chunk-loading missile continue its gameplay flight.
Emission gaps, style changes, entity replacement and large position jumps reset
continuity rather than drawing a line across an inactive/teleported part of the
path. Death or stopped source emissions end delivery; existing client puffs then
fade on their normal mod-owned lifetime. Slightly late births are aged using
those particles' own update methods, with bounded age correction; stale samples
are discarded. Native emissions suppress/trim overlapping relay segments, without
requiring client and server clocks to match exactly.

Rendering copies actual source tessellator quads during native rendering. It
reuses original textures, animated frames, colours, alpha, brightness and blend
state rather than reimplementing every particle's appearance or calling its
renderer twice. MC Heli's deferred smoke is captured at its actual queued draw.
DH's replay runs after all Forge world-last handlers and before held-item depth
clearing. The replay uses DH's modern GL backend, a private VAO/VBO, GLSL 150, and triangles (no legacy immediate mode). Active default/emulation programs are saved and restored; they are not mistaken for shader packs. Extra fragments inside the native far plane are discarded; DH terrain
and the main framebuffer depth hide distant effects. Scene depth is not written.

## Bounds and remaining limits

- Per world: at most 1024 active emission streams; no world/entity/chunk scans.
- Per subscriber: at most 32 observed streams per two-tick packet, rotating
  admission under overload; packets are capped at 8192 bytes.
- Client queue: eight batches; at most two batches processed and 128 remote
  particle births per tick, including child particles.
- Rendering: a shared default budget of 128 extra quads, maximum 512. Complex
  source particles contribute at most two quads at distance. Stable sampling
  thins the whole trail under overload rather than retaining only its oldest end.
  Geometry is sorted back to front; no live particle/world records persist in
  the frame buffer. Large scenes can show reduced density or dropped updates.

Shader packs and special MC Heli camera modes remain unsupported by the extra
pass. DH fog tint is not yet reproduced. Runtime compatibility, camera alignment,
terrain occlusion, full-flight handover and actual frame/network cost still need
in-game checks. Automated shader checks do not establish modpack performance.

The reviewed smoke, exhaust and particle-based explosion sprites from all three
mods can be extended when available locally. This is **not** a complete relay of
all explosion notifications or NTM nuclear cloud entities: their separate
server notification ranges and custom entity renderers need a later bounded
adapter. Missile body/model tracking is unchanged. No gameplay, existing packet,
registry, NBT, world-save or terrain-LOD storage format changes.

## Automated checks

```sh
./gradlew :dh:forge17:checkDistantEffects :dh:forge17:processResources --offline --console=plain
clang -Wno-deprecated-declarations scripts/tests/distant_smoke_gl_test.c -framework OpenGL -o /tmp/dh-smoke-gl-test
/tmp/dh-smoke-gl-test dh/forge17/src/main/resources/assets/distanthorizons/shaders/distant_smoke.vert dh/forge17/src/main/resources/assets/distanthorizons/shaders/distant_smoke.frag
```

The Java checks cover source exclusions/boost phases, inactive-gap resets, spatial
handover, wire validation/limits, copied geometry, sorting and overload admission.
The macOS Core-GL checks compile/link the production shaders and exercise far
visibility, native/DH occlusion, near/far ownership, transparency, lightmap use,
alpha thresholds and unchanged scene depth. No new test dependency is required.

### X6: Angelica particle viewport handoff

Angelica's particle redirect uses its normal terrain viewport, including the native far plane. This can reject smoke before its render method runs, even when relay packets arrive. DH dispatches the original particle renderer directly for supported effects intersecting the native far boundary, inside the configured range and camera side planes. This bypasses Angelica's native-far rejection for those effects without changing particle bounds or simulation. Ordinary particles and the global particle-culling option are unchanged. `culling handoffs` counts these dispatches per frame; actual replay remains subject to the existing quad budget and original particle lifetime.

### X7: live-pack hook order and reversed depth

Live inspection of the X6 game showed that Angelica's separate mixin config replaced the original dispatch calls after DH had wrapped them, leaving DH's wrapper unused. X7 applies a narrow final dispatch patch from the capture mixin postApply hook after injection. It captures the remaining real particle calls and adds an extended-view gate to the generated Angelica particle redirects, with no Angelica class dependency. Missing redirects remain optional. Nested wrappers for the same particle share one capture scope. The replay shader now uses DH's actual depth direction for terrain occlusion; reversed-depth sky no longer rejects smoke. The OpenGL test includes reversed-depth sky, foreground/background terrain, native terrain and mode switching.

X7 validation: final-dispatch fixture executed both particle layers through a separately configured culling redirect, preserved normal rejection and near-particle capture, and captured distant particles exactly once. Applying the same production transformer to the live X6 EffectRenderer bytecode found four capture sites and both culling gates. This is bytecode/runtime-fixture validation, not in-game visual acceptance. No running-game code, settings or files were replaced during inspection.

### X8: NTM interpolation handoff and RPG diagnosis

NTM decoy missiles inherit the tier-1 trail scale (0.5), ordinary base contrail, server chunk ticket and client position interpolator. The interpolator holds still when its tracker updates run out. A tracked client entity is no longer sufficient to suppress an advancing relay segment: only spatially covered emission is trimmed. While valid HBM relay samples are arriving, native client contrails defer to those samples, preventing a stale local emitter from creating a separate stationary trail. Delivery timeout returns control to native emission. No gameplay entity is moved or kept alive.

The installed BFMC RPG-7V2 uses `rpgammo`, whose native trail is `flansmod.fmsmoke`, fuse 200 and drag 0.975. `/dheffects probe rpg7 400 15` exercises that exact factory without a projectile, chunk loading, flight physics or fuse. Status now reports received samples and successful remote particle births per family, plus NTM handoffs. A stopped server projectile cannot be continued cosmetically without inventing a flight path. No gameplay chunk loading is added.

NTM missile-body LOD investigation: the existing standard renderer uses the shared `missileV2` mesh and a decoy-specific texture. The particle relay has no missile identity/skin or independent active-body lifetime, and smokeless/custom-engine missiles may have no emissions. Body visibility therefore needs an independent bounded pose/identity channel with explicit end/expiry, interpolation of observed positions, a separate toggle and a cached low-detail mesh. It must share DH terrain occlusion and hand over at native clipping/tracking boundaries without creating gameplay entities, requiring HBM classes, extending chunk simulation, or manufacturing exhaust. This addition is not included in X8; silhouette, terrain occlusion and native/LOD handover require visual acceptance.

`Trail delivery` diagnostics expose per-family received/born counts and age/distance of the latest HBM/FMUR sample. FMUR `EntityFMSmoke` keeps its 16-tick particle lifetime and native alpha fade (`1 - age * 0.1`), including its approximately half-second visible portion.

### X9 investigation build: NTM flight lifecycle trace

The stable stationary HBM probe narrows the reported blipping to moving-flight delivery/handover. The tested X8 client received 150 HBM samples across the observed tests, with the last endpoint about 194m from the player and zero reported drops. The server recognized the decoy adapter. This does not establish why that flight ended or paused. X9 adds one start/end server log line per slot-zero HBM stream, identifying entity death, world change, or more than five ticks without source updates, with entity position/age, chunk presence and chunk-ticket presence. This is diagnostic instrumentation, not a claimed correction for the remaining moving-trail bug. No server settings, particle lifetimes, renderer budgets or entity simulation are changed.

### X10: descending NTM relay ownership

Once an accepted NTM sample gives the relay ownership, its geometry is no longer
trimmed against an older native client emission marker. Previously a marker
ahead of a delayed descending segment could suppress the entire segment, despite
native emission already deferring to the relay. Other families keep their spatial
duplicate trimming. No new flight positions, longer smoke lifetimes, gameplay
changes or weapon-mod changes are added. The regression covers a descending
segment with the native position ahead; in-game visual acceptance remains needed.

### X11: native particle viewport and NTM identity

NTM relay ownership now uses the packet's world-local entity ID, matching the
client tracker, instead of the independently generated client entity UUID.
Ownership records remain bounded, expire and clear with world changes.
Reviewed particles within the extended camera side planes may bypass Angelica's
terrain viewport on either side of the native far plane. Near quads render in
the original pass with native depth; only far fragments enter the bounded DH
replay. This closes the assumption that viewport rejection and projection
clipping occur at the same distance. No terrain depth checks are disabled.
The reported impact flash is not yet established to have the same cause.

### X12 diagnostic: final descent draw state

The reported X11 flights update to death at impact, and close-range handoffs now
increase. The remaining gap is not claimed fixed. Diagnostics sample Angelica's
actual particle draw projection far plane and fog enabled/mode/end once per
frame, without altering GL state. Native-side quad counts distinguish geometry
that DH leaves to the native draw from geometry eligible for replay. This tests
the prior assumption that DH's saved native projection matches the particle
pass and checks whether fog hides geometry before that boundary. Particle
lifetimes, geometry admission, budgets and networking remain unchanged.

### X13 diagnostic: automatic flight history

A bounded 24-entry client history samples four times per second during HBM
relay delivery and for one second after delivery becomes quiet. It then writes
the history to the ordinary client log, prefixed `DH NTM history`. No precisely
timed screenshot or command is needed. A quiet stream may mean impact, loss of
range or interrupted delivery; the log does not assert entity death. Multiple
simultaneous missiles share the bounded history and flush once all HBM delivery
is quiet. World changes clear it. Rendering and particle lifetimes are unchanged.

### X15: preserve native NTM emissions nearby

The same-position effects-off comparison showed a continuous original NTM trail.
Near tracked missiles now keep their original emission rather than having it
suppressed merely because server samples arrive. Native emission resumes 64m
outside the native clip boundary (radius capped at the original 350m factory
range), providing overlap before the missile crosses into the native view. Only actual
native particle births record coverage; fresh coverage can trim a relay
segment. Native markers now use world-local entity IDs, matching relay packets,
instead of client-generated UUIDs. Markers older than two world ticks cannot
suppress HBM segments. Far tracked missiles retain relay ownership so stalled
client interpolation does not replace authoritative distant emissions. No
source-mod changes, lifetime extension, invented path or new packet is added.
Automatic diagnostics remain. Visual acceptance is still required.

### X16: remove native NTM emission cancellation

X15 still showed a close-range disappearance with uninterrupted relay receipts.
The X8–X15 native-emitter cancellation and its ownership/radius state are removed.
The optional HBM wrapper always invokes the original contrail operation; it only
attributes particle births. Fresh native births retain spatial duplicate trimming
of relay segments. Native particles, source timing and lifetime remain under NTM's
control at every distance. Existing relay and far-geometry budgets remain active.
A compiled-wrapper regression check rejects a branch or return before the original
operation. This is a client-only correction with the existing server protocol;
in-game visual acceptance remains outstanding.


### X17: observation-only NTM pipeline trace

X16 was independently verified loaded and still has a user-confirmed visual gap.
X17 changes no emitter, lifetime, relay trimming, selection budget, shader or
protocol. Automatic flight history now includes `NTM stages v1`, separating
native/relay/unattributed births, active-list membership, actual rocket-flame
age ranges, render dispatch, all near/far source quads and final selected geometry
by camera-depth intervals around the native far boundary. Full/partial coverage
trimming and queue evictions are counted explicitly. Counts are bounded and
sampled; selected/submitted geometry is not proof of visible pixels.
See `NTM_TRAIL_INVESTIGATION_2026-11-04.md` for evidence and interpretation.


### X18: NTM near smoke after terrain fade

The controlled X17 test changed only `vanillaFadeMode` from `DOUBLE_PASS` to
`NONE` in memory. The user reported no gap; the log confirms 228 HBM samples
and automatic restoration to `DOUBLE_PASS`. The prior 128→512 quad-budget test
left the same gap. This isolates the fade/compositing interaction much more
strongly than emitter or budget hypotheses.

X18 retains terrain fading and moves the native-visible geometry of the exact
reviewed `com.hbm.particle.ParticleRocketFlame` renderer after the late fade.
The original renderer still executes once per particle and generates its ten
quads. After validating and copying every needed near quad, DH withdraws that
particle's append from the shared native tessellator, adjusting raw index,
vertex count and manually-added vertex count together. It then draws those
near fragments once after world-last handlers, using the native projection,
source texture/blend/alpha/light values and real native fragment depth. DH
terrain depth is checked where its projection is valid; very close particles
before DH's near plane still use native projection/depth correctly. Neither
pass writes scene depth. Near/far fragment ownership is mutually exclusive.

The near copies preserve native append order and all source near quads; they
are not thinned to the far budget. A lazily allocated 40000-quad buffer matches
the vanilla layer bound of 4000 particles × ten quads. Uploads use the existing
512-quad staging buffer in chunks. The far reservoir remains default 128,
hard maximum 512, with the existing two-quads-per-particle limit. The diagnostic
`NTM post-fade near=captured/drawn fallback=n` identifies actual relocation and
fallback. These counts still do not prove visible pixels.

Unknown source topology, insufficient counters, or full near storage leaves the
original native append intact. Other HBM renderer classes, MC Heli, FMUR,
missile emission, source lifetimes, relay packets, server code and Crucible are
unchanged. Disabling HBM extension/master effects keeps original native drawing.
With terrain fade disabled, near relocation is unnecessary and bypassed.

Validation: 82 core checks and 26 Core-GL pixel/depth checks, covering strict
source/topology eligibility, bounded capacity, append order, near/far ownership,
close native projection, alpha/light state, native/DH occlusion and unchanged
scene depth. Full in-game X18 acceptance with normal terrain fading remains
required, including dedicated server and integrated singleplayer support.
