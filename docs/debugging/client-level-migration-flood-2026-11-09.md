# X47 client level migration failure — fixed in X50

## Diagnosis

Investigated on 9 November 2026 using the supplied X47 jar and
`fml-junk-earlystartup.zip`. The jar manifest, mod metadata and executing stack
traces identify `3.3.4_X47`; its embedded commit is
`0158b52ea90fd6308dabf91957202d8742c2e341`.

The chat warning is “Client level loading failed … LODs may not appear.” The
underlying exception occurs before opening the affected level's database:

```text
Could not move old save data folder:
[<minecraft>/Distant_Horizons_server_data/host.wargames.uk.27005/DIM5]
to [<minecraft>/Distant_Horizons_server_data/host.wargames.uk.27005/DIM5].
at ...core.level.DhClientLevel.<init>(DhClientLevel.java:130)
at ...core.world.DhClientWorld.createClientLevel(DhClientWorld.java:137)
```

Both paths identify the same directory. The Windows client rejects the rename.
DIM2 has the same failure. Counts below are from `fml-client-latest.log` only;
times use the log's displayed timezone, which was not identified.

| Dimension | First failure (time; line) | Last failure (time; line) | Attempts |
| --- | --- | --- | ---: |
| DIM5 | 14:43:02; 16938 | 14:47:39; 5035505 | 159,320 |
| DIM2 | 14:48:30; 5041891 | 14:51:40; 9523960 | 142,288 |

These are 301,608 repeated loading failures, not distinct corrupt LOD sections.
`latest.log` contains 301,588 first-line chat warnings. The archive expands to
approximately 2.94 GB, including overlapping FML and child-process captures.
Raw logs and player-specific installation paths are not included here.

`ClientOnlySaveStructure` can use a readable world name such as `DIM5` for the
active folder, while its pre-2.3 lookup also returns `DIM5`. The constructor
created the destination before checking for an old folder, then attempted the
self-move. Even a fresh dataset can trigger this: regeneration is not a durable
remedy. The same constructor and storage code remained in the checkout at the
start of investigation (`973b70ad1`).

After construction fails, `createClientLevel()` returns null, which
`computeIfAbsent()` does not retain. Subsequent render and fade passes retry and
queue another warning, causing the flood.

The captured session also supports temporary relief after reconnecting. DIM5
initially starts at 14:31:08, but revisiting it after spawn triggers the flood at
14:43:02. After reconnecting at 14:47:42, spawn and then DIM2 initially load;
returning through spawn and revisiting DIM2 triggers the flood at 14:48:30.
A full game restart is not captured.

## Change

- Skip migration when canonical paths or existing filesystem identities match.
- Move a distinct legacy directory only to an absent destination, before creating
  that destination. Do not request replacement.
- Prefer an existing active destination while preserving separate legacy data;
  never merge datasets. Reject regular files as save directories.
- Wait 30 seconds between failed construction attempts per level identity. Log
  each actual failure, but send chat only on the first failure until recovery or
  unload. Success, unload and connection shutdown clear the relevant state.
  Waiting for the server's level key does not count as a failure.

No player data was edited. These logs establish a folder migration error, not
SQLite corruption; the databases were not supplied for an integrity check.

## Validation and test artifact

The following root-wrapper commands succeeded:

```sh
./gradlew :dh:core:test :dh:forge17:check --offline --console=plain
./gradlew check assemble --offline --console=plain
./gradlew :dh:core:test :dh:forge17:checkDistantEffects --offline --console=plain
```

All 116 core tests passed, including seven new migration/retry regressions.
They cover DIM5/DIM2 reopening, normalized aliases, migration retaining database
and WAL bytes, preservation of two existing datasets, fresh directories,
regular-file rejection, retry suppression, independent levels and state reset.
The explicit effects runner passed 16,901 assertions. Forge's standard test
task is configured as skipped; its check/compilation completed successfully.

Packaging advanced the automatic build counter from X49 to X50 and produced
`build/libs/DistantHorizons-3.3.4_X50.jar` (43,350,993 bytes). Manifest and mod
metadata report X50; the packaged constructor and client world reference the
migration helper and retry gate. SHA-256:

```text
bdd90635a6d6b69e7e2a42028e032fa6cd5d99e1dd97d3c33016b3ca0b764bed
```

The tests ran on macOS. Live Windows acceptance remains outstanding: replace
only the client DH jar, retain the existing LOD folders, repeatedly travel
spawn → DIM5 → spawn → DIM5 and repeat with DIM2 and after reconnecting. Verify
LODs load and the migration exception/chat flood do not recur.

## Companion-mod compatibility

X47 and X50 have byte-identical optional weapon-effect mixins (all ten), source
adapters, late loader, relay and sample classes. This fix requires no companion
mod or server update; the existing X47 relay can serve the updated clients.

The earlier post-X47 camera work requires FMUR X198 for the paired PiP/thermal
fix, including MCHeli cameras using FMUR's shared renderer. Older FMUR retains
its earlier camera behaviour. No additional MCHeli or NTM update was identified
for the DH changes since X47. This does not guarantee arbitrary historical or
third-party builds: some existing NTM renderer injections require their target
methods, and no live matrix of older companion jars was tested.
