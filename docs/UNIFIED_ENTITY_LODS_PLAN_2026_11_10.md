# DH-owned player, vehicle and weapon LODs

Status: implementation in progress, 10 November 2026. Actor rendering migration is not yet enabled.

## Scope and branch safety

- DH owns distant visual delivery, render scheduling, terrain coverage decisions and depth integration. Flan's/FMUR and MCHELI remain the owners of gameplay, assets, model definitions and their animation rules.
- Work in DH first. Optional adapters may initially live entirely in DH. Make narrowly scoped companion changes only where a supported ownership, animation or state interface is needed; do not remove legacy implementations.
- Do not change WGMap or remove its terrain/occlusion systems. Existing unrelated consumers remain intact. NTM should need no source changes; if essential, use its current default branch as requested.
- Current companion baselines: MCHELI main `3c111ec`, FMUR main `00cc485`; both have new `feature/dh-lods` branches. WGMap remains unchanged on main.
- DH tested source is `010aa4c95` on `feature/dh-3.3-wdg`. Local main was promoted through merge `c8e94d2f6`, preserving that exact file tree and retaining divergent upstream history. Implementation branch: `feature/mchr-fmur-player-lods`.
- The temporary `feature/dh-unified-entity-lods` branch and old branches remain available. No resets, deletes or force pushes are needed.
- Main's previous upstream changes include a large rendering API migration, stale Forge ticket release, and player-wrapper guards. They are retained in history, not included in the tested tree. Review useful fixes separately; do not silently introduce that renderer migration here.
- Runtime reference: the user's current **Wargames Launcher Prism pack**. Identify its exact instance and installed jar versions before testing. Use a staging copy for jar swaps; do not assume an older development instance is current.
- Use GitHub Desktop for publishing new feature branches. Do not request tokens. Do not push to upstream or publish main without an explicit instruction.

## What the current code establishes

DH's `forge17/effects` package owns the optional NTM/MCHELI/Flan's effects hooks, bounded `DHVisualTrails` relay and native missile mesh presentation. `DistantSmokeRenderer` binds DH terrain depth, native scene depth and supported shader-cloud depth. `distant_smoke.frag` performs per-fragment depth rejection. This establishes a usable rendering mechanism, not universal runtime acceptance.

FMUR's `PlayerLodServer` currently uses a server range default of **512 blocks**, clamped to 1024. It filters dimension, alive/invisibility/team/spectator state, carries appearance revisions and sends bounded snapshots. `PlayerLodRenderer`, `PlayerLodDepthLayer` and `PlayerLodTerrain` own interpolation/model presentation, local self-depth and WGMap terrain-depth integration. The WGMap bridge explicitly requests unloaded-chunk depth coverage.

MCHELI's `VehicleLodRenderDistance` defaults to **750 blocks**, but its description explicitly makes this a **client visual limit**, independent of tracking/radar/locks/missiles. `MCH_RenderFarVehicle` draws loaded aircraft and snapshot-only contacts, preserves animated poses and uses WGMap occlusion. Its existing entity-info stream has other consumers: replacing the visual path must not disable radar, targeting or countermeasure data.

WGMap's `DistantHorizonsWaypointCompatibility` only detects DH and returns false for an external render path. Its world renderer uses vanilla camera-relative placement. The reported moving waypoint defect needs reproduction; its cause is not yet proven.

DH quality presets are client config listeners. A generic common command initializer has an owner permission check, but that alone does not establish the cause in the Forge 1.7.10 GUI. Investigate the actual pack, GUI route, session overrides and command registration before changing permissions.

## Four support toggles

User confirmed the following split. Native nearby rendering continues normally when distant support is disabled.

| Toggle | Distant features it owns |
| --- | --- |
| NTM LOD support | Existing supported NTM missile bodies, exhaust, trails and supported explosion effects. |
| MCHELI LOD support | Supported distant vehicles and their animations, plus MCHELI weapon smoke/trails/effects. |
| FMUR LOD support | Supported FMUR weapon effects; FMUR armour, guns and associated player presentation when Player support is also enabled. **Excludes FMUR vehicles.** |
| Player LOD support | Distant player body, skin, movement/pose animations and ordinary equipment; works without FMUR installed. |

| Player | FMUR | Result |
| --- | --- | --- |
| Off | Off | Neither DH distant players nor FMUR extensions. |
| Off | On | FMUR weapon effects; no DH distant player body or floating player equipment. |
| On | Off | Player body/skin/normal equipment; suppress FMUR-specific distant armour/gun layers. |
| On | On | Player body with supported FMUR equipment and animation integration. |

Explain this dependency in hover text. FMUR armour suppression must not leave body holes from an armour-hiding rule. Toggles must not edit server gameplay state or ordinary native player rendering. Keep one presentation timeline for body, armour and gun; do not spawn independent accessory actors.

Preserve existing saved effect settings (`enableDistantMcheliSmoke`, `extendMcheliTrails`, `extendHbmTrails`, `extendFlansTrails`) through an explicit one-time migration/compatibility mapping. Do not reinterpret saved false values as enabled. Fresh-install support toggles can default on once supported; in-development providers remain gated until their milestone passes. Quality presets must not silently change support toggles or server policy.

## Server authority and visual state

1. Negotiate support and protocol versions before sending distant actors. Server advertises category permissions, maximum ranges and bounded update policy. Missing/incompatible server support means no new snapshot-only actors; nearby native rendering remains usable.
2. Initial DH server maxima: players **512 blocks**, MCHELI vehicles **750 blocks**. Preserve existing effect ranges during the first migration. Operator configuration controls maxima; zero disables a category. Server checks actual viewer/target positions, world, dimension and visibility permissions. Client requests may reduce delivery, never increase the server ceiling.
3. Effective client distance is the minimum of server maximum, client preference and usable terrain coverage. Visibility never generates or force-loads chunks. Rendering distance does not extend simulation, hits, locks or native entity tracking.
4. Reuse existing authoritative data where complete and permitted. Inventory exact animation fields and their origin first: server fields such as limb/swing values must be verified as meaningful on a dedicated server, not merely serialized because they exist. Derive cosmetic walk cycles from authoritative movement when appropriate. Forward gun/reload/fire pose only from existing authoritative state/events; never invent shots.
5. DH owns its visual stream and cache. Prefer small, versioned companion snapshot/provider hooks when direct reuse is unsafe. Do not maintain a second simultaneous full-rate visual stream after takeover; leave independent radar/gameplay streams intact. A DH-only first implementation can use version-checked optional hooks, but must not rely on mutable private stores as a permanent interface.
6. Bound actors, decoded bytes, batch counts, pending queues, appearance NBT/strings and update rates before enabling delivery. Use nearest-first selection and per-viewer spatial candidate reuse; avoid a new world/entity scan per frame or one scan per recipient. Benchmark actual server tick and traffic costs before raising limits.
7. Key actors by connection/world epoch, dimension and stable identity; verify entity IDs against identity. Sequence snapshots, revision appearances, reject stale/wrong-session data, and expire actors after bounded staleness. Remove on range exit, death, disconnect, dimension change or lost permission. Pending appearances stay hidden.
8. Interpolate buffered authoritative poses with bounded latency; use one timestamp for attached parts. Never extrapolate indefinitely or fabricate gameplay entities in the world lists. Teleports reset interpolation. Minimise additional presentation delay on transitions from tracked entities.

## Terrain visibility and rendering contract

**Per-pixel visibility means accuracy against the native/DH terrain actually rendered for that view. It cannot mean block-exact world geometry when DH deliberately renders simplified terrain.** This limitation must remain explicit in acceptance and UI descriptions.

- Reuse the established DH camera-origin conversion, terrain projection/depth direction and native/shader depth integration. Generalise a small reusable view/depth context rather than treating all models as smoke quads or replacing the working effects renderer wholesale.
- Separate unknown terrain from visible sky. A clear depth texel proves neither known air nor a fully covered camera-to-target corridor. A section existing on disk, or `canRender()` returning true, also does not by itself prove complete current-view coverage.
- Default **hide when terrain coverage is unknown**. Track valid, current-world native/DH coverage and gaps across the relevant sight corridor/target envelope, with conservative rejection when coverage cannot be established. Known empty sections may count only with explicit valid-empty provenance. Resolve vanilla/DH overlap, unloaded native sections, pending meshes and changed terrain. Keep metadata tied to render/data generations so obsolete coverage cannot authorise drawing.
- First prove conservative coverage on a bounded prototype. If adequate provenance is unavailable, add the smallest explicit DH coverage contract; do not use WGMap as a hidden fallback. Avoid synchronous GPU readback, disk reads or per-actor unbounded corridor walks. Cache bounded coverage results and invalidate on relevant terrain/view changes. False hiding is preferable to exposing actors through missing terrain.
- Gate detailed animation/model preparation behind category/range/session checks, cheap bounds/frustum rejection and conservative coverage. Reuse suitable DH visibility results, but do not apply terrain-only assumptions to moving actors. GPU depth tests then decide partially covered pixels.
- Depth rejection alone does not save CPU model work. Start with cheap conservative gates, cached static model geometry, pooled frame buffers and bounded updates. Add delayed, nonblocking coarse occlusion queries or hierarchical depth only if profiling justifies them; validate disocclusion and camera movement. Never block on a current-frame query.
- Opaque and alpha-tested models need self-depth, depth writes and correct opaque ordering; transparent rotor/glass/effect layers need the existing material rules and bounded sorting. Preserve normals, lighting, textures, alpha cutouts and glint. The particle material marker and smoke blend state are not a general model material contract.
- Choose one owner per actor and **per view**: native tracked model, legacy distant model, or DH distant model. Secure required data/render resources before takeover; suppress legacy draws only for that object/view. Provide explicit provider capability/version checks and safe rollback. A frame with unknown DH coverage must not accidentally fall back to a legacy through-terrain draw.
- Existing legacy code stays present. In DH mode, suppress only replaced distant visual entry points; never remove underlying state handlers or gameplay behaviour. If a companion hook is necessary for clean ownership, add it on `feature/dh-lods` rather than brittle broad cancellation.

## Scopes, thermals, cameras and equipment

Each render context needs its own view/projection, camera origin, framebuffer, coverage/depth validity and ownership decisions. Main camera matrices must never leak into scope, MCHELI camera, remote/UAV, thermal, shadow or reflection passes.

Inventory supported passes before enabling them. Preserve FMUR thermal heat masks as well as colour; existing `MissileVisual` heat handling is a reference, not proof of full animated model compatibility. Preserve MCHELI thermal/camera rules and FMUR scope zoom/reticle layering. Unsupported or depth-invalid auxiliary passes withhold DH actors, with a diagnostic, until an adapter is validated. Do not silently advertise them as supported.

Player acceptance includes walking, running, sneaking, turning/head yaw, swing, item use, riding, sleeping where currently supported, skin changes, ordinary armour/items, FMUR armour, held guns and supported aiming/reload/fire poses. Vehicle acceptance inventories animated rotors, turret/barrel aim, wheels/tracks, gear, hatches and other parts actually supported by each content model. Snapshot-only animation parity may require companion data additions; record missing fields rather than estimating gameplay actions.

## Quality preset and waypoint work

- Reproduce preset changes as non-op and op in the same server/pack, through GUI and any command route. Trace whether the issue is permission gating, server constraints, config override, failed persistence or a listener restoring CUSTOM. Keep local quality controls available to non-ops while retaining server generation/bandwidth/visibility limits. Report the diagnosed cause before claiming a fix; never remove blanket command permissions as a guess.
- Reproduce waypoint world anchoring by strafing, vertical motion, rotation, third person, terrain transitions and zoom/camera views at known coordinates. Capture the actual render event matrices and compare with native and DH origins. Inspect long-range placement/clamping as well as timing/double camera subtraction. Do not label the report a confirmed DH bug yet.
- WGMap remains the waypoint data/permission owner. First fix DH matrix/GL state leakage if proven. A DH-side optional adapter may consume an existing public snapshot/render contract only if one actually fits. If a proper fix requires a WGMap change, document the smallest interface/fix and defer its edit until authorised; do not duplicate its waypoint store or patch unrelated code merely to stay DH-only.

## Implementation milestones and completion gates

1. **Baseline and diagnostics:** inventory source versions, optional-mod boundaries, pack jars, animation/state fields, draw/thermal events and legacy ownership; capture performance and visibility scenarios. Reproduce preset and waypoint reports. No removal or mass refactor.
2. **Configuration and authority:** implement four toggles/migration, server policy negotiation and validated bounded actor state lifecycle. Add focused protocol/lifecycle/config tests; verify optional-mod absence and dedicated-server loading. No snapshot-only rendering without a negotiated authoritative policy.
3. **Coverage and opaque model proof:** implement reusable view/depth context and conservative unknown-terrain gating; render a bounded moving test model with self-depth, alpha cuts and native handover. Prove full hiding, partial cover and missing-terrain rejection before player/vehicle rollout.
4. **Player baseline:** DH-owned server delivery and plain animated player rendering, skin/ordinary equipment, no FMUR dependency. Integrate legacy ownership hooks where necessary. Verify toggle transitions, appearance repair and no doubled body.
5. **FMUR equipment and effects:** attach supported armour/gun layers to the same pose; route existing Flan's trails through FMUR toggle. Preserve content assets and animation rules; validate thermal/scope contexts. Explicitly exclude FMUR vehicles.
6. **MCHELI vehicles:** integrate validated vehicle visual snapshots/model adapters and animated parts; enforce server 750 ceiling by default; preserve radar/gameplay stream consumers. Route MCHELI trails through its toggle. Validate cameras/thermals, native handover and snapshot-only parity.
7. **NTM regression and reported defects:** connect NTM toggle without changing NTM source where possible. Finish diagnosed preset fixes and the authorised portion of waypoint anchoring work. Unknown external dependency stays clearly reported, not silently marked complete.
8. **Performance and pack acceptance:** measure main/render thread, GPU and server tick/traffic against baseline in the current pack. Test many occluded and visible actors and fast camera movement. Review dedicated-server boundaries and resource lifecycle. Publish coherent feature milestones using GitHub Desktop. Legacy deletion is a separate later decision after acceptance.

Each milestone follows implement, read-only review, minimal fixes, focused tests and final verification. Use the smallest complete milestone; no placeholder framework for future features. This planning turn does not implement these runtime changes.

## Verification matrix

- Automated: effective server/client range and category matrix; old config migration; snapshot sequencing/identity/session resets; malformed/oversized packets; expiry/removal; interpolation/teleport/angle wrap; coverage invalidation/known air/gaps; normal/reverse-Z projection and camera-origin math; ownership on all toggle combinations; dedicated-server/optional-mod classloading.
- Existing regression task: from `dh`, use the 1.7.10 configuration (`-PmcVer=1.7.10`) with `:forge17:compileJava` and `:forge17:checkDistantEffects`, confirming wrapper/task configuration before execution. Add focused checks using the established harness. Run further core checks only where affected. Builds may increment build metadata; report it.
- Actual GPU/in-game: fully hidden/partly exposed hills and structures, missing/unloaded/rebuilding terrain, known empty sky, terrain edits, native/DH boundaries in both directions, near plane, far range, world border and large coordinates, shaders off and supported shaders, changes in DH quality/detail.
- Multiplayer: non-op/op GUI preset changes, server limit updates, death/respawn, invisibility/team/spectator rules, join/leave, reconnect, dimension changes, teleport, packet delay/staleness and native entity ID reuse. Client range cannot cause out-of-policy delivery.
- Compatibility: Player without FMUR; FMUR without MCHELI; MCHELI without FMUR; WGMap absent/present; each support toggle combination; supported scope/thermal/camera views. Ordinary nearby rendering must continue with all four toggles off.
- Performance: report baseline versus candidate under identical pack/world/camera/actor counts: CPU model/animation time, GPU frame time, draw calls, queue/memory bounds, server tick time and bytes per viewer. Hidden/out-of-view actors should incur no detailed model build; overload must degrade within bounds.
- Record screenshots/video and frame diagnostics for partial cover and auxiliary views. Unit tests or compilation alone do not establish pixel visibility, animation parity or acceptable frame time. Acceptance requires runtime evidence; no promise of zero network latency or block-exact simplified LOD terrain.

## Planning-turn result

Only this plan is added. No Java, shader, packet, config or companion source changes yet. Source-history promotion was verified by exact tree comparison with `feature/dh-3.3-wdg`; no runtime tests were necessary for that identical tree. Planned tests above have not been run in this turn.

## Implementation progress — server family controls

- Added server-side NTM/MCHELI/FMUR visual-family switches. The relay checks policy before capture and again before dispatch, including pending NTM blasts, so client subscription bits cannot override a disabled family. Existing radius and bounded queues remain in force. Defaults preserve existing behaviour. This is a cosmetic delivery control, not a claim of authoritative gameplay secrecy for native effects.
- Reused the three existing client config keys for support labels, preserving saved values and the existing opt-in master switch. Tooltips identify pending player/vehicle providers and exclude FMUR vehicles. Player support remains unexposed until functional. Corrected stale blanket shader-unsupported wording.
- Validation: baseline compilation/checkDistantEffects passed 16,901 checks. Changed-source compilation/checkDistantEffects passed 17,291 checks, including all server-family/client-request combinations and invalid family values. Language JSON parsing and git diff --check passed. No jar task/build-number change.
- Dedicated-server review: policy helper uses primitives and existing common TrailSample constants; server config and relay introduce no client imports or optional-mod class links. No protocol IDs or packet layouts changed.
- Runtime still required: toggle each family on a dedicated server with active subscribers; verify delivery stops independently and re-enables on fresh emissions, native effects remain normal, pending blasts are denied, and reconnect works.
- Remaining milestones: authoritative actor negotiation/delivery, explicit coverage provenance, players/equipment/vehicles, view adapters and runtime performance/defect acceptance.

### Subscription admission checkpoint

The relay now aggregates requested family bits from live sessions once per server tick. Source capture requires both a subscriber for that family and server permission. With no permitted recipients, per-world relay work is skipped and visual reservoirs are cleared, while subscriptions remain available for policy re-enabling. Streams for no-longer-requested/allowed families are evicted instead of consuming the bounded reservoir until timeout. This changes neither native emission nor gameplay/radar delivery. Compilation and the 17,291-check regression suite passed again (12 seconds); dedicated-server runtime toggle/expiry scenarios remain unexecuted.

### Coverage investigation

`FullDataToRenderDataTransformer.updateOrReplaceRenderDataViewColumnWithFullDataColumn` returns immediately for null/empty full-data columns. The resulting render source/buffer may therefore contain a mix of known and missing columns. `LodRenderSection.canRender()` only tests a non-null buffer; `gpuUploadComplete()` also tests dirty state. Neither proves complete column coverage. `RenderBufferHandler.buildRenderList` builds the enabled, view-culled section list; actor coverage must use the same view generation without mistaking that list for full column provenance. Next implementation work must propagate immutable known-column coverage with the uploaded buffer generation and explicitly validate coarse-column completeness before authorising an actor corridor. No unsafe section-presence fallback was added.

### Terrain buffer provenance checkpoint

Complete (LIGHT) nonempty source columns now publish a 64-by-64 immutable coverage bitmask through the column transformer, quad builder and exact GPU buffer container. Coarse source generation state is already the minimum of child states, so partial/down-sampled columns remain unknown. Empty/unavailable sources and old upload callers default to unknown. No saved terrain format changed. Coverage bits are collected in the existing transform traversal, with 512 bytes per published mask. The view lookup builds its reusable indexes lazily on the first query, and rejects wrong views, dirty/replaced/disabled/disposed buffers. No actor or effect rendering was enabled by this metadata change.

Validation: compilation plus 37,844 effects/coverage checks passed; targeted NtmStructureLodTest passed all 4 tests, including actual transformer generation-state provenance and existing NTM regressions. The headless integration fixture explicitly disables texturing and restores it afterward. Initial missing import/API compilation issues and fixture failure were corrected before this checkpoint. Core jars rebuilt as task dependencies; no persistent build-number change was recorded. GPU/runtime visibility acceptance remains pending.

### DH player transport and lifecycle checkpoint

Added a separate versioned opt-in `DHPlayerLods` channel with a server-enforced 512-block default (maximum 1024), 64 actors per complete frame, an 8192-byte decode ceiling, bounded/rate-limited requests and a four-frame client queue. Delivery uses one per-world spatial index per update and a bounded nearest-player heap per recipient. Dead/invisible players and FMUR spectators are withheld; an unresolved FMUR policy fails closed. Server/common classes have no client or GL links. No chunks are queried or loaded for delivery. Actor state does not join the client's entity lists.

The immutable client cache validates connection identity, random subscription token, viewer, dimension and increasing sequence, replaces the full recipient set, and expires poses one second after network receipt. Stalled-queue time counts towards expiry. Toggle/world changes clear state immediately and subscription retries repair rate-limited transitions. `/dheffects status` reports delivery readiness and explicitly labels the model provider pending. The experimental client subscription options remain file-only/off by default until rendering is integrated, so ordinary users do not enable an incomplete visual replacement. Equipment, skins, actor drawing and legacy takeover are subsequent work; no player renderer has been cancelled.

Validation: compilation and 54,406 checks passed, including protocol/lifecycle/range checks and bytecode checks for six server-safe actor classes. The complete core suite also passed 117 tests across 25 suites. Runtime packet/visual acceptance has not been claimed.

Current pack identified at `PrismLauncher/instances/Wargames Launcher`, using DH `3.3.4_X50`, FMUR `1.8.06_X206`, WGMap `0.1.2_X161` and Angelica `2.2.14`. No pack jars/configs were modified.

### Model self-depth and compositor checkpoint

Added an unscaled model projection with a narrow near/far interval around the actor, a checked inverse and a reusable/scissored full-resolution model colour/depth capture backend. Captures cap at 8,388,608 pixels and reject incomplete targets or texture feedback loops. The compositor reconstructs physical positions from captured depth, converts camera origins exactly once, compares DH/native terrain depth and writes the selected destination depth. Shader uniforms are cached; model capture state/targets have explicit restoration and release paths. Actor rendering is still gated pending player appearance, view coverage and legacy ownership integration.

Validation: compilation and 54,500 regression checks passed, including 24-bit self-depth reconstruction at distances through 16,384 blocks. A standalone macOS Core OpenGL fixture compiled and executed the real actor shaders: 45 GPU checks passed for normal/reverse depth, transparent/empty capture pixels, native/DH cover, origin conversion and physical distant depths through 4,096 blocks. This does not validate the Java capture backend or native content renderers in-game yet. The physical 4,096-block fixture exposed excessive depth tolerance inherited from the particle design; the new model shader now uses approximately one 24-bit depth step instead of 1e-6, and the occluded case passes. Existing particle shaders remain unchanged.

Angelica native coverage investigation: the installed 2.2.14 uses Celeritas/Embeddium. Public `getAllRenderSections`, section `isBuilt`, `isDisposed`, `getPendingUpdate` and coordinate accessors exist. `isSectionBuilt` alone only checks non-null context data, so it cannot prove terrain is current; the native coverage adapter must also account for pending/in-flight rebuilds and the active world/view. No guessed older Sodium API was introduced.

### Bounded actor terrain gate

The actor view can now combine native compiled-section coverage with the exact DH API render-event identity. A complete 16-by-16 block footprint is required from DH when native coverage cannot establish the relevant vertical corridor. Conservative camera/actor envelopes are checked with a shared 8,192-chunk work budget per view; missing intermediate terrain, missing envelope sides, invalid bounds or exhausted budgets withhold rendering. Checks establish completeness only, leaving partial visibility to the GPU compositor. Native coverage is render-thread-only, world-identity-checked and includes relevant Y sections, rather than treating an arbitrary depth clear value as known sky. The optional Celeritas class is isolated from server/common code and checked against the installed API. Compilation and 54,510 checks passed, including the corridor budget and gap cases; native adapter/backend runtime validation is still outstanding.

### Revision-bound player appearance checkpoint

Player frames now carry appearance revisions under actor protocol version 2 (the new actor channel remains experimental/off by default). A separate bounded raw-NBT appearance message carries the canonical name, signed skin property and five vanilla equipment slots. Payloads cap at 16 KiB, decode has an NBT size tracker, and each recipient update caps appearance delivery at four messages / 32 KiB. Missing revisions precede periodic repairs and oldest repairs rotate first, preventing near-player repair starvation. Oversized/invalid appearances withhold their actor; partial equipment is not silently substituted. Cached appearances must match subscription, connection, dimension, viewer, entity ID and pose revision before a model can use them. The server preserves appearance acknowledgement state across subscription renewals. Team-authorised invisibility and loaded-chunk-only lighting are retained. No appearance NBT is decoded on a network thread.

Validation: Java 8 compilation and 54,675 protocol/coverage/projection checks passed, including malformed appearance frames, byte limits, immutable data, old/new revision isolation and a 64-player repair fairness simulation. Skin/model/equipment runtime acceptance is pending.

Companion FMUR work is now required for safe takeover: its legacy cancellation and username-keyed PlayerData cannot be reused without an explicit ownership/scope hook. Narrow hooks on FMUR feature/dh-lods preserve legacy code and remain inert until a provider registers. Java 8 compilation, 14 scope/ownership checks, 10,110 existing player LOD assertions and existing server-side visibility/class-boundary checks passed. No MCHELI, WGMap or NTM sources changed.

### Primary player renderer integration checkpoint

Actor protocol v3 adds explicit safe/takeover acknowledgements, configurable bounded update intervals, item-use timers and sleeping orientation. The server reads the common item-use field through a read-only mixin accessor rather than calling the client-only Forge getter. A four-sample timeline interpolates movement, orientation, swing and equipment revisions without prediction; previous appearance revisions remain available for delayed samples. FMUR API v2 supplies detached presentation contexts, offhand and face/night equipment, and isolates disabled FMUR layers.

The primary view now wires player rendering into shaderless world-last and Angelica pre-composite. Resources, compatible ownership hooks and current-view terrain coverage must be ready before requesting takeover. Native player draw observations prevent duplicate presentation; far native models hand over only with authoritative recipient records. Detached models are never inserted/ticked in the world. Shared actor depth resolves model overlap, while conservative projected bounds restrict per-model capture and pixel work. Terrain completeness is tied to the actual render-list generation as well as event identity. The API event-copy helper was corrected to retain DH's distinct model-view matrix.

Validation: Forge compile and 63,835 protocol/geometry checks passed; full core suite passed 117 tests across 25 suites; the actual GLSL fixture passed 52 GPU checks, including partial cover, mixed native XY/DH depth and normal/reverse inter-actor depth. These validate components, not the Minecraft/Angelica content-renderer integration. Primary view runtime acceptance, cloud masking, source gun firing/reload animation, auxiliary scope/thermal/camera views, MCHELI vehicles and the reported waypoint/preset issues remain outstanding. Player support remains experimental, file-only and off by default. No pack jars/configs, WGMap or NTM sources have been changed.

### Authoritative FMUR animation and terrain invalidation checkpoint

Actor protocol v4 carries an optional immutable extension of at most 48 bytes per player (12 KiB full-frame decode cap, still 64 actors). FMUR API v3 supplies a fixed 38-byte source pose: per-hand ages of actual server projectile construction, active reload duration/remaining/count, accepted scoped state and minigun speed. Capture is shared across recipients each update. It does not transfer private inventories or shot-intent data. Body/equipment and gun sampling share the same buffered timeline; a future reload start/cancellation cannot leak backwards across its sample. The client applies cosmetic recoil, slide, pump, muzzle and reload fields directly, without invoking gun gameplay, local camera recoil or reload sound helpers. Exact parity for every custom content animation still needs runtime acceptance; this is not a claim of universal model support.

A terrain invalidation revision now survives a late older mesh upload. Invalidations take a separate short-held lock, never a mesh/database/GL lock; coverage rejects stale builds, and upload/buffer publication uses volatile fields. Three regression tests include a cross-thread invalidation before stale completion. Primary player status diagnostics now report drawn/native/unknown/cached counts.

Validation: DH compile and 63,842 component checks passed; the full core suite passed 120 tests across 26 suites. FMUR Java 8 compilation, 2,030 presentation/animation checks, 10,110 existing LOD assertions and expanded server boundary checks passed. Runtime primary/auxiliary-view validation and MCHELI provider work remain outstanding.

Report investigation: no operator-permission check appears in DH's local preset configuration/GUI path. A reproduction is still needed before changing policy. WGMap's distant waypoint display clamps X/Z toward the camera's vanilla range, but its beam endpoints retain unscaled world Y; that is a concrete candidate for distant beam drift/distortion. The optional marker-follows-player-height setting is also intentional behaviour to distinguish from the report. WGMap remains unedited; no fix is claimed without reproducing the observed failure.

### Actor/effect depth ordering checkpoint

The current actor scene can now occlude subsequent DH missile/trail replay using DH depth at native screen coordinates, including shaderless actors beyond the native far plane. Only alpha-complete scene pixels reject effects; transparent/empty pixels leave effects visible. The sampler is enabled only after the current primary actor scene completed, reset each frame, and its extra texture bindings are restored explicitly. This is still the primary opaque/alpha-test integration; intersecting multilayer transparency needs runtime material acceptance and is not claimed universally exact.

Validation: Forge compile and 63,842 component checks passed. The real smoke GLSL/VBO GPU fixture passed 91 checks, adding normal/reverse actor occlusion, foreground smoke, transparent/empty actor pixels and expired-scene rejection. Existing 52 actor GPU checks remain the preceding verified result.

MCHELI companion source API v1 on feature/dh-lods now captures immutable, bounded source-valid vehicle pose/part inputs without touching radar packets, legacy rendering or WGMap. Java 8 compilation and all 228 MCHELI tests (45 suites) passed, including four new snapshot/side-boundary tests. This adapter alone does not provide DH vehicle rendering or impose the future 750-block DH delivery limit; the relay/provider integration remains next.
