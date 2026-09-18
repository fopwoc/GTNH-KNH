# Palimpsest storage architecture

Palimpsest keeps a **time-spatial history of a Minecraft map**: not just what every chunk looks
like now, but what it looked like at any moment since it was first seen, so a world with ten
thousand hours on it can be scrubbed like a video. This document explains how the storage layer
does that, why it is shaped the way it is, and what it measured on a MacBook Pro (M4 Max, 36 GB,
macOS, JDK 26) with the headless suite (`./gradlew -p mods/palimpsest storageSuite`).

All numbers below come from one run of that suite on 2026-09-18. They are synthetic workloads,
not gameplay; the shapes are chosen to be harder than a real world, not easier.

## 1. The model

| Term | Meaning |
|---|---|
| **tile** | one chunk seen from above: 16×16 pixels, one palette byte each (256 colors) |
| **layer** | one observation of a tile: an epoch, a 256-bit coverage mask, and colors for the covered pixels |
| **epoch** | wall-clock milliseconds of the observation; strictly increasing per tile, never coordinated between machines |
| **history** | a tile's layers ordered by epoch |
| **region** | 32×32 tiles, one directory on disk, the unit of opening and syncing |

The map never sends deltas. It sends the whole current view of a tile, as often as it likes, and
the store diffs it against the tile's latest state: unchanged pixels are dropped, a layer with no
changed pixels is dropped entirely, a layer that changes everything is stored whole. Reconstructing
a tile at epoch *E* walks its layers newest-first from the last one at or before *E* and stops as
soon as every pixel has been resolved. A layer that covers all 256 pixels ends the walk.

That is event sourcing with per-pixel granularity, and everything else exists to keep that walk
short, the files few, and the whole thing safe to put in a git repository shared between PCs.

## 2. The stack

```
 map ──observe(tile, colors)──▶ ObservationBroker ──commit (≤1/min/tile)──▶ MapPageStore.append
                                     │ latest view                                   │
 map ◀──latest(page)/historical(page, epoch)── MapPageCache ◀── RegionTileHistoryStore
                                                                    │ one per 32×32 tiles
                                                                 TileHistoryStore
                                              ┌─────────────────────┼─────────────────────┐
                                        write-ahead logs      sealed segments        index sidecar
                                        (local, 2 files)      (.pseg, synced)       (.pidx, local)
```

### ObservationBroker — the queue in front of the database

Block-by-block building would produce thousands of observations a minute near a base, and none of
them deserve to be history. The broker keeps only the **newest view per tile**, serves it for live
rendering immediately, and commits to the store at most **once per tile per interval** (one minute
by default; a tile's first sighting commits at the next tick; everything pending commits on unload).
Because every observation is the whole tile, "accumulating" a minute of changes is just keeping the
last array. A view identical to the last committed one is dropped without touching the store.

Result: the persistent history has minute-level resolution however chatty the map is, and the base
costs at most 64 layers a minute.

### TileHistoryStore — the engine

One per region directory. Its job is to make appends cheap, reads bounded, and files immutable.

**Write path.** An append is normalized against the current tiles, encoded into a *segment image*,
and written to the active **write-ahead log** — a local file of CRC-framed, sequence-numbered
images. The log is not fsynced per append: losing the last seconds of observations after a crash
only means those chunks get observed again, whereas an fsync per append is a visible hitch. Only
after the log write does the store take its exclusive lock, for the few microseconds it takes to
publish the new index entries. Readers never wait on I/O.

**Sealing.** When the active log passes 1 MiB or five minutes, a maintenance tick *seals* it: the
log is frozen, appends switch to the second log, the frozen log is read once, its layers are
grouped per tile and written as **one content-addressed segment** (`<sha256>.pseg`, fsync, atomic
rename), and the affected tiles' index entries are re-pointed under the lock. Two logs alternate so
writes never pause; the seal itself runs outside the store lock.

**Checkpoints.** While sealing, any tile that has accumulated 64 layers since its last
full-coverage layer gets its newest sealed layer written as a **full snapshot** at the same epoch.
No epoch is invented and no older layer is touched; the read walk is simply guaranteed to stop
within 64 records. This is the I-frame / P-frame trick from video codecs, applied per tile.

**Compaction.** Sealing makes small files. Once a region holds eight sealed segments under 4 MiB,
`compact()` merges them into one, rewrites the index of only the tiles they held, deletes the
inputs and leaves their ID slots as tombstones so nothing else moves. Segments over 4 MiB are
never rewritten again, so each byte is rewritten a logarithmic number of times over a region's
life — the size-tiered compaction of LSM trees, tuned for git.

**Read path.** A read binary-searches the tile's epochs, then walks newest-first. A union coverage
mask per 64 layers lets the walk skip whole groups that cannot contribute; per-layer masks skip
individual layers; and record bodies are fetched through a **span reader** that pulls a contiguous
run of a tile's records in one positioned read (2 KiB, growing to 64 KiB only while the walk keeps
going). Sampled reads for zoomed-out pages derive the byte offset of a single pixel's color from the
mask and record length and read just that byte.

### The index

Per tile: one `Long` epoch, one packed `Long` (segment id 21 bits, offset 31, length 9, kind 3) and
one `Int` coverage reference — **20 bytes per layer**. Coverage references address side arrays
chosen by how many pixels a layer covers: a single pixel is stored inline, two to eight are packed
into one `Long`, a full layer is a sentinel, anything else is a 32-byte mask; histories dominated
by large masks switch to inline masks. Duplicate epochs (which only arise when two machines
compacted the same segments independently) resolve deterministically toward the segment whose name
sorts first, so every machine holding the same files agrees.

The index is a **derived, disposable, local** structure. It can always be rebuilt by parsing the
segments, and it is cached in a sidecar (`.index-cache.pidx`): header, one checksummed block per
tile, a tile directory, a trailer. Opening a region reads only the directory; a tile's block is
loaded (via mmap) the first time the tile is touched; clean tiles beyond a 16 MiB budget per region
are evicted LRU. The sidecar is keyed to the exact segment set — a synced or compacted segment
invalidates it — and a damaged block makes the store drop it, rebuild, and retry the read.

### RegionTileHistoryStore and MapPageCache

Regions are opened on demand and kept in an LRU of 512 open regions. The region table's lock only
guards the table; each region's store has its own locks. Above that, pages are 128×128 RGBA rasters
at 13 levels of detail, built outside any lock (a page invalidated mid-build is returned but not
cached), with a separate bounded working set for one pinned historical epoch that is patched
incrementally when the epoch moves: only tiles whose epoch range actually changed are re-read.

## 3. Why git can be the replication protocol

Only `.pseg` files are data; logs, sidecars and temp files are `.gitignore`d by the store itself.
Segments are immutable and named by their content hash, so two machines never produce a conflict:
a merge is a union of files. Appends from both sides land in different segments. Compaction on both
sides can produce overlapping segments; the reader dedupes identical layers and resolves the rare
same-epoch disagreement by segment name. Deleting a small segment after compaction is an ordinary
git deletion, and because only small segments are ever rewritten, repository history grows roughly
with the data, not with the number of compactions.

## 4. What it measured

### The giant world

Built to look like a long GTNH save: **1,048,576 tiles** (1,024 regions), every tile observed once
and edited twice at scattered times, plus an **8×8-tile base** written for **50,000 epochs** (eight
tiles changed per epoch, 1–16 pixels each) while a reader thread rebuilt the base page continuously
and a maintenance thread sealed and compacted every 50 ms.

| | |
|---|---|
| tiles / layers | 1,048,576 / 3,537,494 |
| on disk | **306 MB** of segments (≈ 290 B per chunk ever seen) + 78 MB local sidecars |
| files | 1,032 segments — one per region plus the base's seals |
| cold world generation | 3.1 M layers in 24.5 s |
| hot base appends (500 batches of 800 layers) | **p50 0.72 ms · p99 4.6 ms · max 7.6 ms** |
| reader rebuilding the 64-tile base page *during* those appends | **p50 0.41 ms · p99 0.55 ms · max 10.9 ms** (32,733 rebuilds) |
| background seal of a 1 MiB log | ~40 ms, off the lock |
| fresh process: open region + first base page | 11 ms |
| 64-tile base viewport at latest epoch, warm | **55 µs** |
| whole-world page (LOD 7, 259 regions touched) cold | 162 ms |
| time-lapse step over the whole world, warm | **170 µs** (p99 1.7 ms) |
| resident index for 259 open regions | 16 MB |

The read numbers hold because checkpoints cap the walk: a base tile with ~6,000 layers of history
decodes one or two layers to answer "what does it look like now".

### Long single-tile histories

48 tiles, 2,000 epochs, three edit patterns; then 62,500 epochs of one-pixel edits (a million
layers) as the adversarial case.

| case | layers | on disk | latest read (48 tiles) | historical read, middle epoch | reopen |
|---|---|---|---|---|---|
| sparse (8 px / layer) | 33,024 | 888 KB | 358 µs → **36 µs** after checkpoint | 254 µs | 1.1 ms |
| mixed (rectangles, scatter, full) | 33,024 | 5.8 MB | 166 µs → **35 µs** | 394 µs | 3.4 ms |
| adversarial (1 px / layer) | 33,024 | 440 KB | 47 µs → **35 µs** | 47 µs | 0.8 ms |
| adversarial, 1 M layers | 1,001,024 | **5.9 MB** (6 files) | **44 µs** | 91 µs, 1,472 layers skipped by group masks | **4.8 ms**, 0 bytes resident |

A million single-pixel layers cost 5.9 bytes each on disk and open in under five milliseconds
because nothing is loaded until a tile is read. Appending them took 1.0 s (about a million layers
per second through the log).

### Compaction

Fourteen small segments (1.04 MB) merged into one (888 KB — the merge also removes per-file tile
headers and improves epoch deltas) in 73 ms, with pixel digests verified identical before and
after and reads unchanged.

### Wide world at every zoom

A dense 512×512-tile world (263,296 tiles, 71 MB). Building a 128×128 page at LOD 4 (one pixel per
tile, 16,384 lookups) takes 20 ms cold and **2.6 µs** warm; every farther level samples at most 256
tiles and builds in 12–14 ms cold, about 1–2 µs warm. Segment hashes are verified once per process,
so the second and later passes hash nothing.

### Compression

The codec stores solid and near-solid tiles in 1–35 bytes instead of 256 and sparse edits as
position/color pairs; uniform and near-uniform worlds shrink 15×. Varied per-pixel color does not
compress at all yet — that work waits for real captured chunk colors, since tuning against
synthetic noise would be misleading.

## 5. Where the ideas come from

- Write-ahead log, immutable sealed files, size-tiered compaction, tombstoned file IDs: **LSM trees**
  (LevelDB, RocksDB, Cassandra).
- Append-only facts with as-of reads, snapshots every N events: **event sourcing / Datomic**.
- Full layers as keyframes, sparse layers as deltas, a fixed keyframe interval: **video codecs**.
- Content-addressed immutable files, merge by union: **git**, Perkeep, Dolt.
- Newest value per key, commit on a schedule: **Kafka log compaction**, Redis AOF/RDB.
- Per-tile epoch deltas and varints: time-series compression (Gorilla, Prometheus).

The combination — an LSM engine whose sealed files are content-addressed so a git repository is
the replication protocol, with video-style keyframes so per-pixel history stays cheap — is the part
that is Palimpsest's own.

## 6. Guarantees and limits

- **Durability:** sealed segments are fsynced; up to the last seconds of observations in the log
  can be lost on a crash, by design. A torn log tail is truncated on the next open.
- **Integrity:** segment names are SHA-256 of content and verified once per process (or when a
  file's size or mtime changes); sidecar blocks carry CRC32 and self-heal; structural corruption
  surfaces as `CorruptHistoryException`.
- **Concurrency:** reads run in parallel; appends never hold the store lock across I/O; sealing
  and compaction build off-lock and swap under it; the region table lock only guards the table.
- **Bounds:** ≤ 64 layers decoded per read after checkpoints; 16 MiB resident index per region;
  512 open regions (≈ 2,000 file descriptors — check the launcher's `ulimit`); segments per region
  ≤ 2²¹ − 3.
- **Not done:** compression of varied colors, persisted derived-LOD tiles (the one cost that still
  scales with region count on a cold far-zoom page), and the map itself.
