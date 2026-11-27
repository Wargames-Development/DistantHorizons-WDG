# DH worker audit and scheduling

## Test 9

`1kTest9` used DH X98, WGProfiler X49, the RTX 4060 Laptop and 20 JVM
logical processors. Seven render-loader workers spent 145.77 CPU-seconds
over the 61-second capture. Update propagation spent 28.86 CPU-seconds,
and network compression spent 4.31 CPU-seconds. The old counters are
cumulative from the start of their owning view or worker pool. Subtract
the first sample from the last.

| Measurement | Result |
| --- | ---: |
| Combat render-loader CPU | 17.42 CPU-s |
| Combat propagation CPU | 5.27 CPU-s |
| Combat mesh admissions, initial/evicted and refresh | 44 and 463 |
| Combat render-loader completions | 504 |
| Whole-capture mesh admissions, initial/evicted and refresh | 8,888 and 2,575 |
| Whole-capture render-loader completions | 3,544 |
| Highest sampled render-loader waiting depth | 2,510 |

The 30–40 s combat interval had waiting depth zero in most one-second
samples. The 504 completions are consistent with a steady flow of source
and neighbor refreshes. They do not establish that 504 different visible
terrain meshes changed. The capture reports about 35 ms of render-loader
CPU per net completion in this interval, including work that started
or finished across the sample boundaries. At 43 s, the initial-load
counter jumped by 3,426 and the waiting queue reached 2,510. The queue
then collapsed from 2,209 to eight with only 68 additional completions
between samples. The admission/completion gap is consistent with queue
removal after the view changed. It cannot be used as a canceled-task
count because of sampling boundaries, in-flight tasks and other callers.

Worker CPU samples use interval deltas and the stack seen at the end
of each interval. Many samples ended in a park after the thread had
used CPU. Sorting, clipping, vertical lighting and face emission,
point decoding and material lookup appear in render-worker samples.
These stacks identify work being done, but cannot reliably partition
145.77 seconds among useful meshes, discarded results or synchronization.
There are no before/after hashes or per-admission block sources in Test 9
that would attribute refreshes specifically to destruction.

## Task lifecycles and changes

- **Render loader.** A tree walk selects coverage and schedules resident
  section builds. The worker reads the center and up to four same-level
  edge strips, reduces source columns, generates and merges faces, packs
  buffers and waits for the render-thread upload. The wait bounds memory
  retained by staging buffers. Each neighbor transform previously walked
  all 4,096 positions even though its SQL read supplies only a 64-column
  edge. The transform now uses the same edge bounds as the decoder. This
  eliminates up to 16,128 unrelated column visits per mesh build. The main
  source still visits every column, and existing adjacent seam and water
  lighting inputs use their original strips.
- **Mesh admission and retirement.** The old walk queued every visited
  node that lacked a usable upload, including subdividing roots that the
  selection code never draws and nodes retired later in the same tick.
  DH evaluates the current selection before admitting mesh work. It
  selects only a bounded set of best candidates and retains at most one
  waiting terrain task per configured worker slot under the active
  quadtree producer. The underlying tree supplies remaining candidates
  again as capacity opens. Resident nearby dirty meshes get an admission
  share, with a share also retained for coverage-filling work. A section
  closed during loading skips the next expensive phase where possible,
  and closure and publication share a short lifecycle lock. Existing
  mesh buffers continue to serve terrain while a dirty replacement builds.
- **Mesh refreshes.** A changed source or explicit reload can notify
  its center and four adjacent meshes. Each affected position now
  combines pending notifications before touching mesh state. Revision
  tracking keeps a refresh received during a build dirty until a build
  using the newer revision publishes. Direct source and neighbor-only
  admissions have distinct counters. The code still refreshes neighboring
  borders because the old diagnostics supply no equivalence test for
  material, lighting and geometry at a seam. Exact duplicate meshes
  across separate source revisions and useful-but-rebuilt post-eviction
  meshes remain separate questions for a matched replay.
- **Propagation.** Persisted parent/child flags feed a bounded queue.
  Workers group upward updates by parent, acquire parent before child
  locks, decode source data, merge and save changed data. Downward
  propagation now clears its parent's persisted child flag once, while
  retaining the parent lock across the four child passes. This removes
  up to three equivalent SQLite updates and lets the child lock go sooner.
  Rejected submissions release their in-memory dedup reservation so the
  persisted request can be retried. Parent lock misses and data comparisons
  that found no changed target now have counters.
- **Compression and scheduling.** Network receive and response handling
  hand compression, decoding and request continuation work to DH's shared
  picker. Test 9 shows a smaller aggregate CPU cost here than in meshing
  or propagation. The picker remains work-conserving within the configured
  global worker limit. During a backlog, eligible pools with fewer admitted
  tasks get the next free slot, with least-recent dispatch breaking ties.
  It avoids sorting the pool list, coalesces pause wakeups, preserves wake
  requests that lose the scheduler lock, and resizes workers in place
  when the configured limit changes. Existing movement and server health
  gates and the render loader's lower Java thread priority still apply.
  A parked render worker retains its slot until upload completion. There
  is no new fixed combat concurrency cap.

For a mesh that actually needs geometry, the current renderer uses the
source and seam data, face generation and a published buffer set. The
observed capture cannot establish how many of its refreshes needed those
steps. The changes above remove full-grid neighbor scans, obsolete or
duplicate admissions, repeated writes of the same parent flag, and some
scheduler work. Required refresh, streaming and propagation work can be
rescheduled under contention. Compression payload processing and the
render-thread upload remain necessary for their respective inputs and
have not been assigned a speculative savings estimate.

## Measuring the new build

Keep WGProfiler X49 with `-Dwgprofiler.dhCacheDetails=true`. Trace adds the
following under `dh_cache` in `client_render_metrics.csv`.

- `mesh_source_refresh_total`, `mesh_neighbor_only_refresh_total`,
  `mesh_backpressure_ticks_total` and `mesh_cancelled_before_start_total`
  distinguish refresh origin and admission pressure.
- `mesh_published_*`, `mesh_discarded_*` and `mesh_failed_or_missing_*`
  record completed worker CPU and elapsed time by result. A mesh publish
  can still leave a pending source revision, counted separately. Elapsed
  time includes upload wait where applicable.
- `*_executed_*` records worker execution time by pool, and
  `worker_picker_dispatch_*` records dispatcher work. CPU sample counts
  distinguish missing JVM thread timing from zero CPU time. These scopes
  can nest in one another. Parallel elapsed time cannot be summed into
  frame time.
- `propagation_*` reports parent lock misses, changed targets and
  no-change targets. Target counts and whole-pool CPU do not establish
  the per-target cost of lock waits or comparisons.

Compare at 512 chunks on both target systems using the same populated
cache, route, zoom, effects and destruction. Use Benchmark for average FPS,
1% lows and P95/P99, and Trace to compare worker outcomes and queues.
Include the rapid movement or zoom transition that caused the later
burst. Compare desired and selected coverage during the run, terrain
update latency after destruction, and occlusion near newly exposed
borders before attributing any improvement to the scheduler.

## Regression checks

From `dh`, `gradlew.bat --offline -PmcVer=1.7.10 :core:test
:forge17:checkDistantEffects` passed. All 135 core tests passed. The tests
exercise concurrent submissions and fast completions, I/O dispatch during a
mesh backlog, and refreshes that arrive during a build. The Forge check
passed its terrain-copy and marker GL state checks and verified the Angelica
API in the build dependency.

From the repository root, `gradlew.bat --offline build -x test` built
`build/libs/DistantHorizons-3.3.5_X100.jar` for Forge 1.7.10. The release
build advanced the build number from X98 at the start of the work through
X99 for the first build and X100 after the publication counter correction.
