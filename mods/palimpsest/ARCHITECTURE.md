# Palimpsest storage architecture

Palimpsest keeps a **time-spatial history of a Minecraft map**: not just what every chunk looks
like now, but what it looked like at any moment since it was first seen, so a world with ten
thousand hours on it can be scrubbed like a video. This document explains how the storage does
that, why it is shaped the way it is, and what it measured with the headless suite
(`./gradlew -p mods/palimpsest storageSuite`) on a MacBook Pro (M4 Max, 36 GB, macOS, JDK 26).

Numbers come from two runs on 2026-09-19: the previous per-tile layer store (the *baseline*) and
the persistent quadtree that replaced it. They are synthetic workloads, not gameplay; the shapes
are chosen to be harder than a real world.

## 1. The model

| Term | Meaning |
|---|---|
| **tile** | one chunk seen from above: 16×16 cells of facts — block id, height, liquid depth, biome |
| **block id** | index into a machine's vocabulary `blocks.<machine>.tsv`, which froze the block's color and tint flag on first sight |
| **tile record** | one version of a tile: full (every cell) or a delta against the previous version |
| **node** | a square of the quadtree at level *k* (2^k tiles per side): four child refs, one sample per child, the newest epoch below |
| **sample** | one cell's facts standing for a whole subtree: its first present quarter's sample, down to the tile's centre cell |
| **root** | the level-22 node of one commit, stamped with the commit epoch; the list of roots is the history |
| **ref** | where a record lives: segment and offset |
| **segment** | an append-only file of records; sealed at 4 MiB, renamed to its SHA-256, listed in the machine's manifest |
| **slice** | one directory of segments: the map looked down from one ceiling (`y255/` is the surface) |

The map never sends deltas. It sends the whole current view of a tile as often as it likes, an
[ObservationBroker](src/main/kotlin/io/github/fopwoc/mods/palimpsest/map/ObservationBroker.kt)
keeps the newest view per tile and commits at most once per tile per minute, and a commit is one
[MapTree.commit](src/main/kotlin/io/github/fopwoc/mods/palimpsest/tree/MapTree.kt): every changed
tile gets a record, every node on the path from it to the root gets a copy with the new child, and
everything else is shared with the previous root by reference. That is a persistent data
structure in the functional sense — git's tree objects over pixels — and everything below follows
from it:

- **Reading the map at a moment** is a binary search in the root list, then a descent. Nothing is
  replayed; an old root is a complete map.
- **Zooming out** reads nodes, not tiles. A page pixel at LOD *L ≥ 4* is a level *L−4* square,
  and its facts are the sample its parent keeps for it; a 128×128 page is 4096 parent nodes,
  whatever the world size. Below LOD 4 a page decodes the tiles it shows.
- **What changed between two moments** is a parallel descent of two roots that stops wherever
  the child refs are equal. Cost is proportional to the changed area, not to the world or to the
  history. The historical page cache uses it to drop only the pages that differ when the time
  slider moves.
- **Identical tiles are one record** whenever the writer notices (a tile whose facts equal its
  current version is skipped), and a commit that changes nothing writes a root and nothing else.

Two conscious departures from the baseline's principles: node samples are derived data that *is*
persisted and synced — deterministic copies of one cell, never summaries, and the reason a far
zoom never touches a tile — and each commit is one root, so time resolution is the broker's commit
cadence (one minute), which was already the promise.

## 2. The stack

```
 mod ──observe / tick / flush / close──▶ WorldMap ◀──view.frame(camera, time)── screen
                                            │
        ObservationBroker ──commit (≤1/min/tile)──▶ MapPageStore ◀── MapView (IO workers, ready pages)
              │ latest view                              │
                                                  MapPageCache ◀── PageBuilder ◀── TerrainShader
                                                        │              │
                                                        └─ changed ─▶ MapTree ◀── BlockTable (ids, frozen colors)
                                                                        │
                                                                    SegmentSet
                                                                        │ one per machine
                                                                  active-<machine>.pseg ──seal──▶ <sha256>.pseg
```

### Tile records — cost follows change

[TileCodec](src/main/kotlin/io/github/fopwoc/mods/palimpsest/tree/TileCodec.kt) writes each of the
four channels through [ChannelCodec](src/main/kotlin/io/github/fopwoc/mods/palimpsest/tree/ChannelCodec.kt),
which picks the smallest of: one value for the whole grid; a local palette with indices packed at
`ceil(log2 n)` bits; for full grids, residuals against the median-edge predictor of the west,
north and north-west neighbours (LOCO-I), packed at the width the largest residual needs — heights
are smooth, so this is mostly 0 and ±1; or raw. A delta record carries only the cells that differ
from its base (positions, or a mask above 32 cells) through the same channel coder, and a full
record is forced after 16 deltas so no read follows a longer chain.

| tile shape | bytes per tile (including its share of nodes) |
|---|---|
| uniform | 30 |
| near-uniform (one odd cell) | 65 |
| terrain bands (four block rows, height steps) | 161 |
| hills (three blocks, two biomes, rolling heights) | 196 |
| varied (noise in block and height) | 574 |

The plain encoding is 1,536 bytes per tile. Entropy coding of the packed residuals (rANS with a
per-record model) is the next step on this axis and is expected to take terrain to roughly half.

### Segments — immutable, hashed, per machine

[SegmentWriter](src/main/kotlin/io/github/fopwoc/mods/palimpsest/tree/SegmentWriter.kt) stages a
commit's records into one CRC-framed group and appends it in a single write; readers on other
threads see a group entirely or not at all, and the whole active segment stays in memory so fresh
records never touch the disk. A torn tail is truncated on the next open. Sealing writes a trailer
(root list, machine slots), fsyncs, renames the file to its content hash and appends the name to
`segments.<machine>.txt`. Refs inside a file name other segments by *machine slot* and ordinal,
so a segment written after a git sync can point into another machine's files, and a segment can
be opened on any machine by its manifest alone. Sealed segments are memory-mapped on first use.

### Rendering — facts to pixels at page build

[PageBuilder](src/main/kotlin/io/github/fopwoc/mods/palimpsest/render/PageBuilder.kt) fills a
129×129 grid of facts (one border row and column so slopes at the page edge see their
neighbours) from tiles or node samples, and [TerrainShader](src/main/kotlin/io/github/fopwoc/mods/palimpsest/render/TerrainShader.kt)
turns it into RGBA: the block's frozen color, the biome tint where the block takes one, the
vanilla map's slope shading against the northern neighbour, water shaded by depth. Every rule
lives in the shader and none in the history, so a better look repaints the past too.

## 3. Why git can be the replication protocol

Only sealed `.pseg` files, manifests and vocabularies are data; the active file and the machine
id are `.gitignore`d by the store itself. Segments are immutable and named by their content, each
machine writes only its own segment files and its own manifest and vocabulary, so a merge is a
union of files with no conflicts. Reading a map that holds two machines' root chains (overlaying
their trees, newest subtree wins) is the one piece not yet written; today a directory is read as
one machine's history plus whatever foreign segments its refs reach.

## 4. What it measured

### Reads, where the tree wins

The 48-tile viewport (8×6 tiles, 16×16 cells each) read 120 times, warm; "historical" is the
middle of the history.

| case | versions | baseline latest | baseline historical | tree latest | tree historical |
|---|---|---|---|---|---|
| sparse (8 cells / edit) | 33,024 | 36 µs (358 µs before checkpoint) | 232 µs | **75 µs** | **80 µs** |
| mixed (rectangles, scatter, full) | 33,024 | 35 µs (166 µs) | 328 µs | **26 µs** | **24 µs** |
| adversarial (1 cell / edit) | 33,024 | 35 µs (47 µs) | 45 µs | **24 µs** | **22 µs** |
| adversarial, 1 M versions | 1,001,024 | 44 µs | 85 µs | **23 µs** | **23 µs** |

A historical read costs the same as a live one because there is nothing to replay. The sparse
case is slower than the others only because its deltas are more numerous per tile (chains of up
to 16 to decode cold); after the entropy-coding step the chain bound can be revisited.

A page of a dense 512×512-tile world (335,872 tiles), one page at LOD 4..12, cold in the same
process, and the whole thing from a fresh process (open + page):

| LOD | tiles under the page | baseline cold (16 / 256 open regions) | tree cold | tree from a fresh process |
|---|---|---|---|---|
| 4 | 16,384 | 53 / 27 ms | **10.7 ms** | 13 ms |
| 5 | 65,536 | 114 / 58 ms | **6.7 ms** | 8.6 ms |
| 6 | 262,144 | 383 / 239 ms | **3.7 ms** | 10.7 ms |
| 7 | 1 M | 93 / 15 ms | **1.8 ms** | 9.1 ms |
| 8–12 | 4 M – 1 G | 13–39 ms | **1.2–1.8 ms** | 7.5–8.3 ms |

Every page above LOD 4 reads exactly 4,095 nodes and no tile; the baseline opened one region per
sampled tile. A historical page at any of these levels is 50 µs–2.4 ms (baseline 0.3–300 ms).

Giant world (1024×1024 tiles, an 8×8-tile base with 50,000 commits, reader and sealer running
alongside):

| | baseline | tree |
|---|---|---|
| cold generation (1 M tiles observed, revisited twice) | 3.1 M layers in 27 s, 298 MB | 1.1 M records in 12 s, 193 MB |
| hot base commits | append of 100 epochs p50 0.72 ms | one commit p50 **50 µs**, p99 130 µs |
| reader rebuilding the base page during writes | p50 0.42 ms | p50 **0.26 ms** |
| fresh process: open + base page | 11.6 ms | **0.5 ms** |
| base viewport (64 tiles), warm | 55 µs | **18 µs** |
| whole-world page (LOD 7), cold | 157 ms, 259 regions opened | **1.4 ms**, 5,436 nodes |
| time-lapse step, whole world | 169 µs | 320 µs |
| time-lapse step, base page | ~1 µs (nothing to patch) | 2.5 ms (64 tiles re-decoded) |

### Writes, where the tree pays

Path copying costs about one node per level per changed cluster. In the synthetic histories
every commit changes 16 scattered tiles of a 32×32 world, so each commit rewrites ~62 nodes
(≈2.8 KB) on top of ~16 small deltas:

| case | baseline on disk | tree on disk | of which nodes |
|---|---|---|---|
| sparse, 2,000 commits | 888 KB | 7.2 MB | ~5.7 MB |
| mixed, 2,000 commits | 5.8 MB | 15.9 MB | ~5.7 MB |
| adversarial, 2,000 commits | 440 KB | 6.4 MB | ~5.7 MB |
| adversarial, 62,500 commits | 5.9 MB | 203 MB | ~178 MB |
| giant world hot base, 50,000 commits | ~8 MB | 129 MB | ~112 MB |

This is the structural price of "every commit is a complete map": it is per *commit*, not per
tile. In play the broker makes at most one commit per minute per tile and the changed tiles are
clustered around the player, so a day of continuous building is a few thousand commits of a few
KB each — tens of MB, not hundreds. Two mitigations are on the table if that ever matters: one
root per minute globally instead of one per due tile (bounds commits to 1,440 a day at no loss of
the promised resolution), and delta nodes ("same as that node, but this child") for the levels
where a commit changes one child out of four.

Reopen is the root list: 2–4 ms for 2,000 roots, 34 ms for 62,500 (one positioned read per root
to resolve its ref; a trailer that carries the refs would make it a scan of 13 trailers).

## 5. Where the ideas come from

- Path-copying persistent trees, structural sharing, "a commit is a root": **git**, Clojure's
  persistent vectors, Datomic, ZFS/Btrfs copy-on-write.
- Samples in internal nodes so a coarse view reads coarse nodes: **mipmaps**, quadtree LOD in
  terrain engines.
- Per-record adaptive shapes and the median-edge predictor: **JPEG-LS / LOCO-I**, PNG filters.
- Append-only, CRC-framed groups, immutable content-addressed files, merge by union: **LSM
  trees**, git, Perkeep.

The combination — a persistent quadtree whose nodes double as the mipmap and whose sealed files
are content-addressed so a git repository is the replication protocol — is Palimpsest's own.

## 6. Guarantees and limits

- **Durability:** sealed segments are fsynced; the active segment is written per commit but not
  fsynced, so a crash can lose the last commits, which are simply observed again. A torn tail is
  truncated on open; a group with a bad CRC ends the valid region.
- **Integrity:** segment names are their SHA-256; structural damage surfaces as
  `CorruptTreeException` from the record that found it.
- **Concurrency:** one commit at a time (the game thread); reads run in parallel on IO workers
  against immutable records and a published-length snapshot of the active segment.
- **Bounds:** ≤ 16 deltas per tile decode; 22 node reads per tile lookup, cached in a 64k-node
  LRU; 4,095 node reads per page above LOD 4; block ids are 16-bit per machine vocabulary.
- **Not done:** entropy coding of records, dedup of identical full records across the world,
  packed Morton-ordered snapshots for sequential cold reads, multi-machine overlay reads, history
  thinning.
