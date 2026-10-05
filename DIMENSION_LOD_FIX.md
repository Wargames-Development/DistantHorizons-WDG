# DH dimension LOD fix

Built for Minecraft 1.7.10 / Forge 10.13.4.1614 from local HEAD
`aaea89e3d8e7bdb5a582d23d4d1018dd1596098c` plus the changes below.
The reported symptom and logs point to DH lifecycle defects. WGMap and WGEvents
were inspected as references and were not changed. Runtime confirmation is still required.

## Installable artifact

`build/libs/DistantHorizons-WDG-dimension-lifecycle-fix.jar`

SHA-256: `F8EDE44409A16082504306C3EEDBE63DF3C3A777B00B8061EDB7EDB5538E03C8`

This is the normal remapped release jar, renamed to distinguish it from the
installed preview. Replace the existing DH jar on both the client and dedicated
server while they are stopped; keep only one DH jar on each side. The live pack
and server were not modified. Keep existing configuration and LOD databases.

## Changes and review findings

- **B1, fixed:** Dimensions loaded after login had no LOD request handlers for
  already-connected players. `DhServerWorld.java`, `DhClientServerWorld.java`,
  and `AbstractDhServerWorld.java` now register those players when creating a
  level and tolerate missing origin levels during dimension changes.
- **B2, fixed:** `ForgeServerProxy.java` created a fresh wrapper for unload,
  missing the identity-keyed DH level. It now passes the cached load wrapper.
- **B3, fixed:** Session callbacks could retain unloaded levels.
  `AbstractDhServerLevel.java` owns removable, idempotent per-player scopes;
  `ServerPlayerState.java` removes that level's rate-limit state on cleanup.
  Logout and unload detach callbacks before closing resources.
- **B4, fixed:** `ScopedNetworkEventSource.java` registered multiple parent
  forwarders for one message type, duplicating every local handler call.
  It now registers one forwarder per type and clears local handlers on close.
- **B5, fixed during review:** `WorldGenerationQueue.java` cleared the shared
  executor when closing one dimension. It now cancels only its own work.
- **B6, fixed during review:** `ChunkGenEvent.java` skips already-cancelled
  events; `DhInternalServerGenerator.java` checks the active DH level on the
  owning server thread before queued chunk request, release, or unload work.
- `ScopedNetworkEventSourceTest.java` adds four regression tests covering
  single dispatch, isolated scope closure, closed registration, and repeated
  scope replacement.

These files are under `dh/coreSubProjects/core/src/`, except the Forge proxy
under `dh/forge17/src/main/java/` and the generation wrappers under
`dh/common/src/main/java/`.

Review verdict: **ACCEPTABLE WITH MINOR ISSUES** (T1/T2 below). No new
client-only dependency was introduced into the dedicated-server path. Chunk
guards execute on the server thread. No packet IDs, serialization, database
formats, dimension identities, permissions, or terrain-visibility policies changed.
No migration is required. Callback storage follows connected-player and loaded-level
lifetimes; no new work queue was introduced.

## Validation

- **PASS:** `./gradlew.bat :dh:core:compileJava :dh:forge17:compileJava --console=plain`.
- **PASS:** Final `./gradlew.bat :dh:core:test --tests tests.ScopedNetworkEventSourceTest collectJars :dh:forge17:check --console=plain`.
  Four regression tests passed. Forge17 has no enabled automated runtime tests.
- **PASS:** `git diff --check`; all ten changed production classes are present
  in the delivered jar with Java 8 class-file version 52; delivery hash matches
  the remapped release; jar contains no local Git/cache/log or regression-test files.
- **PASS:** Release bytecode inspection confirms unload calls the cached-wrapper accessor.
- **T2:** The root `build` and `collectJars check` commands failed because the
  wrapper's Minecraft development/test preparation requires a missing Java 17
  toolchain. The DH release build above succeeds with the existing toolchains.
  No build scripts or installed Java versions were changed.
- **T1:** Dedicated-server boot and live client rendering were not executed.

## Required runtime checks

1. Restart client and dedicated server with this jar. Without relogging, use
   WGEvents travel: lobby -> map A -> map B -> map C -> map A. Confirm LODs
   appear beyond vanilla render distance after every transition.
2. Leave a map empty until Forge unloads it, then revisit it without relogging.
   Confirm the server logs `Closed DHLevel` for the unloaded world and creates
   a fresh DH level on return; check for ticket force/unforce errors.
3. Keep another player in a different map while one map unloads. Confirm their
   LOD generation continues. Repeat logout/rejoin and normal server shutdown.
4. If singleplayer is used, repeat dimension travel and save/reopen there.

The primary remaining uncertainty is behavior in the running modpack,
particularly unloads during active generation. Broader pre-existing cache and
generator-reference retention, and general DH memory tuning, are outside this fix.
No roadmap features were added or deferred from an accepted implementation milestone.

Server databases remain at each dimension's save folder plus
`data/DistantHorizons.sqlite`: the supplied logs show
`spawnWorld/data/DistantHorizons.sqlite` and
`spawnWorld/DIM2/data/DistantHorizons.sqlite` (similarly DIM10 and DIM11).
