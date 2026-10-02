# Production checkpoint compared with 2.2.2

The released tag already contains the generation 2.3 persistent quadtree, immutable history
segments, whole-tile content reuse and node samples for LOD rendering. Generation 2.4 retains
that model and improves its encoding and repeated access. This comparison measures the actual
tag's storage and renderer against the production checkpoint, rather than combining percentages
from intermediate experiments.

## Measured difference

Three alternating pairs run in separate JVMs on the same M4 Max/APFS machine, with Kotlin and
Compose compiler 2.4.20, language/API level 2.1, JVM target 21, JDK 26.0.2.1 and a 9 GiB maximum
heap. Tables report medians of the three runs; percentile entries are medians of per-run
percentiles. Complete individual results are in [results.txt](results.txt).

| History workload | Tag bytes | Checkpoint bytes | Reduction | Generation, tag → checkpoint |
|---|---:|---:|---:|---:|
| 1,024 tiles, 2,000 commits, eight scattered pixels per changed tile | 4,413,916 | 3,208,566 | 27.3% | 527 → 490 ms |
| Same area/history, rectangles, scatter and whole-tile replacements | 13,527,164 | 12,390,950 | 8.4% | 1,430 → 1,220 ms |
| Same area/history, one pixel per changed tile | 3,270,000 | 2,080,430 | 36.4% | 368 → 331 ms |
| 1,001,024 tile versions, 62,501 commits | 103,757,464 | 63,906,626 | 38.4% | 12.22 → 10.51 s |
| Wide world: 67,108,864 dense surface columns, plus distant observations | 17,113,867 | 14,427,407 | 15.7% | 2.18 → 2.19 s |
| Giant world: 268,435,456 columns, initial mapping and two sparse revisits | 32,105,821 | 26,753,486 | 16.7% | 7.00 → 6.96 s |
| Giant world after 50,000 hot-area commits | 111,280,664 | 83,297,759 | 25.1% | Additional 16.83 → 16.48 s |

These sizes count sealed history segments and their indexes, excluding vocabularies,
filesystem allocation and latest-only region files. Empty active headers add another 26 bytes.
The large fixtures deliberately repeat terrain patterns with fixed height, depth and biome:
1,048,320 of the giant world's initial tiles link to existing content in both versions. These
are complete top-down tiles, not a 3D volume or a prediction of real-world map size. The wide
fixture has 335,872 observed tiles overall, or 85,983,232 columns including distant observations.
The million-version case occupies only 262,144 distinct columns; history depth and spatial
coverage are different dimensions.

The giant hot phase deliberately pauses for 20 ms every 100 commits, adding at least ten
seconds to its elapsed time. A page reader and segment sealer run concurrently. Their scheduling
changes seal boundaries, so hot-history byte totals vary slightly between rounds.

| Operation | Tag | Checkpoint | Meaning |
|---|---:|---:|---|
| Giant-world commit p50 / p99 | 71 / 217 µs | 66 / 210 µs | Modest improvement under concurrent load |
| Invalidated base-page refresh p50 / p99 | 231 / 342 µs | 36 / 76 µs | 6.4× faster at p50, 4.5× at p99 |
| Sparse one-cell refresh p50 / p99 | 256 / 352 µs | 41 / 110 µs | Reuses terrain facts and shades affected patches |
| Edit outside LOD 3 sample centers p50 / p99 | 399 / 607 µs | 23 / 68 µs | Checkpoint reuses the same raster for all 256 measured frames |
| Coarse LOD 7 refresh p50 / p99 | 50 / 80 µs | 26 / 47 µs | Faster repeated sample access |
| Dense 64-tile refresh p50 / p99 | 220 / 299 µs | 139 / 193 µs | Faster than the tag in this fixture |
| Mixed-history reopen | 2.43 ms | 7.89 ms | A real startup regression |
| Million-version history reopen | 28.49 ms | 43.99 ms | 54% slower despite fewer bytes |
| Wide-world LOD 7 first page, after construction | 2.06 ms | 2.07 ms | Essentially unchanged |
| Wide-world LOD 7 fresh open + build + close | 7.52 ms | 12.15 ms | 62% slower |
| Giant-world first base page, after construction | 0.535 ms | 0.751 ms | 40% slower |

The refresh fixture contains 1,024 complete tiles. Each case warms 64 frames and measures 256;
timers include invalidation, tree reads and CPU shading, but exclude terrain writes and full
pixel verification. All 320 frames per case are checked against an uncached full build. Complete
pixel streams match between versions, as do all historical/latest viewport hashes in the storage
suite and after reopening. The first checkpoint sparse run has a worse p99 than the tag
(766 versus 352 µs), while the other two are 90 and 110 µs. The median improvement is not a hard
latency guarantee. The worst concurrent-reader sample also rises from 8.99 to 12.15 ms across
all runs even though typical refreshes improve substantially.

Dense refresh previously regressed against the implementation immediately before incremental
shading. It improves against 2.2.2 here because the comparison includes intervening read/index
changes as well. Different baselines and fixtures answer different questions.

Fresh opens clear decoded caches but leave the OS file cache warm. The checkpoint additionally
authenticates complete sealed segments against their SHA-256 filenames before publishing readers.
That adds real first-open work; smaller files therefore do not imply faster reopening. These
measurements do not isolate hashing from root-index construction, patch reconstruction, allocation
or JVM effects. Coarse timelapse steps show no convincing whole-world improvement: nine steps
following the initial step have a median 432 → 453 µs. That tiny sample is not a reliable tail
estimate. GPU upload, actual game tint lookup, frame scheduling and client scanning are unmeasured.

## Architectural and behavioral difference

| Responsibility | 2.2.2 | Production checkpoint |
|---|---|---|
| Canonical history | Persistent quadtree, full/delta tiles and patched nodes | Same model, inherited channels/samples and shorter relative references |
| History file version | Segment format 3 | Segment format 4, design generation 2.4 |
| Temporal publication | Flat immutable root arrays copied on append | Immutable pages of at most 256 roots with backward links |
| Repeated viewport reads | Separate root-to-tile walks | Bounded resolved tile-window indexes |
| Page refresh | Drop raster and rebuild whole page | Retain owned sample facts, refresh dirty tiles, shade patches or fall back to full shading |
| Latest-only operation | No separate latest-only store | Mutable compressed 32×32-tile regions; saved history remains browsable |
| Minimap height | Saved surface view | Separate volatile height views, room-ceiling selection above the whole player, cached visible terrain |
| Height transition | No separate height publication | Scan required tiles before publishing a coherent replacement; keep previous view visible |
| Scanning | Chunk-count limit | Separate minimap/surface passes and client-thread time budgets |
| Recovery and publication | Existing active-tail recovery and segment framing | Bounded delta depth after reopening, restored content indexes, vocabulary-before-batch publication and sealing on orderly map close |
| Sealed integrity | Framing/trailer checks | Complete filename SHA-256 authentication before publishing a reader |
| Map features | Terrain, history and minimap | Persisted waypoints, camera-based HUD projection, item icons, GTNH prospecting/aura-node/claim layers |
| Incompatible maps | Format mismatch on open | Preserve incompatible slices separately, then open fresh storage; no conversion |
| Divergent machine continuations | File union does not materialize a causal merged view | Still not a production feature; isolated merge tests and prototypes now exist |
| Height-independent 3D history | Absent | Still an isolated prototype |

The surrounding framework adds platform camera projection and item rendering, including
preserved item metadata. Build artifacts are collected into loader/version folders. The
checkpoint comparison tests storage/rendering behavior, not these integrations in a running game.

Retained sample planes add 266,256 array bytes per nonempty cached page. Fully occupying the two
128-entry live/historical tables raises their sample/pixel arrays from about 16 MiB to 81 MiB,
roughly **65 MiB extra per map store**. That excludes GPU textures, minimap caches, decoded tiles,
view buffers and object overhead. This is a calculated capacity bound, not a measured retained-heap
result. Page-count limits do not provide a whole-session byte budget.

## Technical assessment

This is a worthwhile production improvement over the tag: the history is consistently smaller,
large-world generation throughput is broadly preserved, and repeated page refreshes avoid much
of the previous work. The result is an evolution of generation 2, rather than adoption of the
experimental 3D or causal database.

The next priorities are to profile the first-open regression and apply a shared byte budget to
retained rendering facts. Integrity should remain intact while startup work is reduced. These
are more immediate production targets than universal payload borrowing, which saved little on
captured maps, or persistent compiled historical planes, which amplified edited histories.
Causal merge remains the architectural work needed to fulfill the divergent-PC continuation
requirement. Modern negative/tall world heights also remain limited by the existing byte-height
representation; this checkpoint does not deliver arbitrary vertical history.

## Reproduction

Run from the repository root with the project's Gradle/JDK environment and cached dependencies.
Choose a fresh directory outside any real map. The helper extracts sources through read-only Git
operations and never opens an installed game's map files.

```bash
export REPORT_DIR="$(mktemp -d /tmp/map-checkpoint.XXXXXX)"
./gradlew :palimpsest:checkpointClasspath \
  -I mods/palimpsest/experiments/checkpoint/classpath.init.gradle.kts --offline --no-daemon
python3 mods/palimpsest/experiments/checkpoint/compile-baseline.py "$REPORT_DIR"
python3 mods/palimpsest/experiments/checkpoint/run.py "$REPORT_DIR"
```

The baseline compiles every tag tree/render source, the tag page cache/key/raster, and the tag's
existing benchmark fixtures. Only the new shared refresh harness comes from the checkpoint.
Baseline classes precede the current runtime on the classpath. Framework/runtime dependencies
and compiler settings are held constant: this is a matched subsystem comparison, not a launch
of the complete old mod. The inherited build-metadata label in temporary suite reports comes
from the shared runtime and does not identify the baseline implementation; committed results
instead label each implementation explicitly and omit dates and build metadata.

The compiler helper is fixed to the versions used for this comparison. A current-only invocation
is also available as `:palimpsest:checkpointExperiment --args=<fresh-directory>`. Repeating a
comparison against a later checkpoint requires recording that checkpoint separately; the
committed results belong to the production behavior described here.
