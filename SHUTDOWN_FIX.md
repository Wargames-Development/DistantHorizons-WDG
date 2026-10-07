# DH shutdown completion fix

Built for Minecraft 1.7.10 / Forge 10.13.4.1614 from
`37f381bec652acbc6c4c9dd26688c0a6cc1cc5ba` plus this change. Includes the
previous dimension LOD fix. The user reports no crashes with that previous jar;
one brief Windows not-responding episode resolved itself. This change addresses
a confirmed code defect, not a proven cause of that particular pause.

## Installable jar

`build/libs/DistantHorizons-WDG-dimension-and-shutdown-fix.jar`

SHA-256: `57322F65B4A3078842E85872013C90E584B889EE645655DF8103C3AB114FF215`

Replace the previous DH jar while the client/server is stopped, keeping only
one DH jar per installation. Use the updated jar on both sides. Existing LOD
databases and configuration remain compatible. The live pack/server were not edited.

## Implementation and review

**B1, fixed:** All three world shutdown paths launched a background level-close
thread whose completion future was only signalled after a successful close.
An exception or error killed the worker and left the world waiting forever.

Files under `dh/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/world/`:

- `LevelShutdown.java`: package-private helper that completes futures on
  success or failure, including thread creation/start failure. Its wait loop
  reports failures and waits for every remaining task.
- `DhClientWorld.java`, `DhClientServerWorld.java`, `AbstractDhServerWorld.java`:
  replace duplicate thread/wait code with that helper, allowing existing world
  map cleanup and timer cancellation to execute after a failed level close.

`dh/coreSubProjects/core/src/test/java/com/seibel/distanthorizons/core/world/LevelShutdownTest.java`
adds five tests for successful completion, exceptions, errors, waiting for other
levels after a failure, and cancelled futures.

Review verdict: **ACCEPTABLE**, with **T1** runtime confirmation outstanding.
The helper uses Java 8 APIs and existing common logging, with no Minecraft
client-only imports. Existing level-close thread ownership, parallelism, and
resource-close order are preserved. No new worker pool, queue, public API,
packet, config key, save format, permission, or visibility policy was introduced.
No migration is required. The earlier teleport fix remains included.

## Validation

- **PASS:** `./gradlew.bat :dh:core:test --tests com.seibel.distanthorizons.core.world.LevelShutdownTest --tests tests.ScopedNetworkEventSourceTest collectJars :dh:forge17:check --console=plain`.
  Five shutdown and four dimension-network regression tests passed; release build
  succeeded. Forge17 runtime tests are disabled in the existing build.
- **PASS:** `git diff --check`; packaged bytecode inspection confirms all three
  world classes invoke both helper methods, and all four changed production
  classes target Java 8 (class version 52).
- **PASS:** Delivered jar hash matches the remapped release. No local Git/cache/log
  or regression-test artifacts were found in the jar.
- **T1:** Live Minecraft and dedicated-server scenarios were not executed.

The tests deliberately simulate close errors, so their captured error log
messages are expected. The full wrapper development build was not rerun; its
previously identified missing Java 17 tooling is separate from the successful
release build command above.

## Manual checks and remaining scope

1. Join multiplayer, travel across maps, disconnect, and repeat. Confirm return
   to the menu and continued LOD loading on subsequent joins.
2. Enter singleplayer, explore or generate LODs, save and quit, then reopen it.
   Repeat while generation is active and verify saved terrain remains intact.
3. Stop a dedicated server with active LOD requests and multiple dimensions.
   Confirm shutdown finishes and inspect any reported close failures.

This prevents an already-thrown close failure from stranding the completion
wait. A close routine that itself blocks indefinitely remains outside this fix;
no timeout or forced abandonment of saving/generation was added. A failed level
close is logged, but this change cannot guarantee all that level's resources
were released or its pending writes completed. Broader shutdown performance,
render-thread ownership changes, and partial-resource recovery are deferred.
No commits, branches, or published artifacts were created.
