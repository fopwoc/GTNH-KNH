# Palimpsest storage architecture

Palimpsest keeps a **time-spatial history of a Minecraft map**: not just what every chunk looks
like now, but what it looked like at any moment since it was first seen, so a world with ten
thousand hours on it can be scrubbed like a video.

This document describes how the storage evolved. Each generation is presented as a coherent data
model, with its design decisions and measured tradeoffs. Generations 1 and 2 were the map's own
top-down storage; generation 3 keeps whole chunks in 3D and lives in its own library,
`lib/palimpsest-db`.
Early benchmarks use a MacBook Pro with an M4 Max, 36 GB of memory, macOS and JDK 26;
measurements with different conditions identify them alongside the results. Synthetic workloads
stress the engine rather than predict the size of a particular player's world.

```mermaid
timeline
    title Storage generations
    Gen 1 : Per-tile layer histories in an LSM engine
          : colors pre-shaded and palette-quantized per pixel
          : write-ahead logs, sealed content-addressed segments, checkpoints, compaction
          : local index sidecars, 32×32-tile regions
    Gen 1.5 : Channels — colors + biomes as separate byte planes
            : 16-bit channels as two planes
    Gen 2 : Persistent quadtree, one root per commit
          : tiles store facts (block, height, depth, biome), colors rendered at page build
          : node samples make far zoom read nodes, never tiles
          : structural diff between any two moments
    Gen 2.1 : Write amplification cut in half
            : one commit per minute, patch nodes, rooted squares, relative epochs and refs
    Gen 2.2 : Range-coded tile records
            : adaptive models, west-neighbour and gradient contexts, terrain 3× smaller
    Gen 2.3 : Identical tiles stored once
            : content hashes in trailers, link records of ten bytes
    Gen 2.4 : Inherited samples and channels, shorter refs
            : compressed current regions, viewport tile indexes
    Gen 3 : Whole chunks in 3D, in a library of their own
          : the map's views derived into rebuildable local indexes
          : section deltas, unchanged chunks cost nothing
          : a synced folder instead of a git repository
```

The goals never changed: look like the game, make time travel and time-lapse instant at any zoom,
read less data when zoomed out instead of summarizing, freeze colors on first sight so a resource
pack cannot rewrite history, and carry the history between machines. Generations 1 and 2 did the
last one with a git repository; generation 3 with a plain synced folder.

---

## Generation 1 — per-tile layer histories (retired)

### The model

| Term | Meaning |
|---|---|
| **tile** | one chunk seen from above: 16×16 pixels, each one byte — a palette entry, pre-shaded |
| **layer** | one observation of a tile: an epoch, a 256-bit coverage mask, colors for the covered pixels |
| **history** | a tile's layers ordered by epoch |
| **region** | 32×32 tiles, one directory on disk, the unit of opening and syncing |
| **palette** | 256 colors derived once from the modpack's block textures by weighted median cut; a *tintable* band of greys for blocks the game colors per biome |

The map sent the whole current view of a tile and the store diffed it against the tile's latest
state: unchanged pixels were dropped, a layer with nothing changed was dropped entirely.
Reconstructing a tile at epoch *E* walked its layers newest-first from the last one at or before
*E* until every pixel was resolved. Event sourcing with per-pixel granularity.

```mermaid
flowchart LR
    Mod -- "observe(tile, colors)" --> Broker[ObservationBroker<br/>newest view per tile,<br/>commit ≤ 1/min/tile]
    Broker --> Store[MapPageStore]
    Store --> Regions[RegionTileHistoryStore<br/>one per 32×32 tiles]
    Regions --> Engine[TileHistoryStore]
    Engine --> WAL[(write-ahead logs<br/>local)]
    Engine --> Seg[(sealed .pseg<br/>content-addressed, synced)]
    Engine --> Idx[(index sidecar .pidx<br/>local, disposable)]
    Store --> Pages[MapPageCache<br/>128×128 RGBA, LOD 0–12]
    Pages --> View[MapView] --> Screen
```

### The engine

**Write path.** An append was normalized against the current tiles, encoded into a segment image
and written to the active write-ahead log — CRC-framed, sequence-numbered, not fsynced per append
(losing the last seconds after a crash only means those chunks are observed again). Only then did
the store take its lock, for microseconds, to publish index entries.

**Sealing.** Past 1 MiB or five minutes the log was frozen, appends switched to a second log, and
the frozen log became one content-addressed segment (`<sha256>.pseg`, fsync, atomic rename).

**Checkpoints.** A tile with 64 layers since its last full layer got its newest sealed layer
written as a full snapshot at the same epoch, so no read walked more than 64 records — the
I-frame / P-frame trick from video codecs, per tile.

**Compaction.** Eight sealed segments under 4 MiB were merged into one, size-tiered like an LSM
tree; the reader tolerated the same layer arriving from two machines' compactions and kept one
deterministically.

**Read path.** Binary search on the tile's epochs, then a newest-first walk with union coverage
masks per 64 layers to skip whole groups, per-layer masks to skip layers, and a span reader that
pulled a contiguous run of records in one positioned read. Sampled reads for zoomed-out pages
derived the byte offset of one pixel from the mask and read just that byte.

**The index.** 20 bytes per layer in primitive arrays (epoch, packed location, coverage
reference), cached in a checksummed sidecar keyed to the exact segment set, loaded per tile on
first touch, evicted LRU under a 16 MiB budget per region.

```mermaid
sequenceDiagram
    participant P as Page build (LOD ≥ 4)
    participant R as Region store
    participant I as Index
    participant S as Segments
    P->>R: for each sampled tile (256..16,384)
    R->>R: open region if needed (≈0.6 ms)
    R->>I: binary search epoch, walk masks
    I->>S: positioned read of one color byte per contributing layer
    S-->>P: pixel
```

### What it measured

| workload | result |
|---|---|
| 48-tile viewport, latest, warm | 35–36 µs after checkpoint (166–358 µs before) |
| 48-tile viewport, middle of history | 45–394 µs |
| 1 M single-pixel layers | 5.9 MB on disk (5.9 B/layer), 44 µs reads, 4.8 ms reopen |
| dense 512×512-tile world, one page cold | LOD 4 20–53 ms · LOD 6 240–380 ms · LOD ≥ 7 13–19 ms |
| giant world: hot base appends (800 layers) | p50 0.72 ms · p99 4.6 ms |
| giant world: whole-world page (LOD 7) cold | 157–162 ms, 259 regions opened |
| giant world: time-lapse step | 170 µs |

### Why it was retired

Two limits were baked into the data, not the code:

1. **The look was frozen in the pixels.** 231 palette entries for every GTNH block and the vanilla
   map's dim shading were applied *before* storage. Machines collapsed to the same grey, water
   snapped to one blue, and no rendering improvement could ever reach old history.
2. **Every zoom level and every moment was a per-tile walk.** A far-zoom page opened one region per
   sampled tile; a historical page replayed each tile's layers; comparing two moments meant
   comparing everything. The index made each step fast, but the number of steps was the world
   size, not the screen size.

A **channel** step in between (colors and biomes as separate byte planes; a 16-bit channel as two
planes) fixed EndlessIDs' 65,536 biome ids but not either limit, and made clear that the store
should hold *facts* and let the renderer decide the look.

---

## Generation 2 — a persistent quadtree (retired)

### The model

| Term | Meaning |
|---|---|
| **tile** | one chunk seen from above: 16×16 cells of facts — block id, height, liquid depth, biome |
| **height** | one byte, 0–255; worlds that reach below 0 or above 255 (26.2 spans −64..319) flatten to those ends |
| **block id** | index into a machine's vocabulary `blocks.<machine>.tsv`, which froze the block's color and tint flag on first sight |
| **biome** | 16 bits: the registry id on GTNH (EndlessIDs goes past 255), a hash of the biome key on 26.2, where registry ids differ between packs |
| **tile record** | one version of a tile: full (every cell) or a delta against the previous version |
| **node** | a square of the quadtree at level *k* (2^k tiles per side): four child refs, one sample per child, the newest epoch below |
| **sample** | one cell's facts standing for a subtree: its first present quarter's sample, recursively down to the tile's centre cell |
| **root** | the level-22 node of one commit, stamped with the commit epoch; the list of roots is the history |
| **ref** | where a record lives: segment and offset |
| **segment** | an append-only file of records; sealed at 4 MiB, renamed to its SHA-256, listed in the machine's manifest |
| **slice** | one directory of segments: the map looked down from one ceiling — the top of the dimension is the surface, `y255/` on GTNH and `y319/` in a 26.2 overworld |

The broker is unchanged: newest view per tile, commit at most once per tile per minute. A commit
is one [MapTree.commit](https://github.com/fopwoc/GTNH-KNH/blob/2.2.2/mods/palimpsest/src/commonMain/kotlin/io/github/fopwoc/mods/palimpsest/tree/MapTree.kt): every
changed tile gets a record, every node on the path from it to the root gets a copy with the new
child, everything else is shared with the previous root by reference — git's tree objects over
pixels.

```mermaid
flowchart TB
    subgraph commit t1
        R1((root t1)) --> A1[node A] --> T1[tile x]
        A1 --> T2[tile y]
        R1 --> B[node B] --> T3[tile z]
    end
    subgraph commit t2: tile x changed
        R2((root t2)) --> A2[node A']
        A2 --> T1b[tile x' → delta on x]
        A2 -. shared .-> T2
        R2 -. shared .-> B
    end
```

Everything below follows from that shape:

- **Reading the map at a moment** is a binary search in the root list, then a descent. Nothing is
  replayed; an old root is a complete map.
- **Zooming out reads nodes, not tiles.** A page pixel at LOD *L ≥ 4* is a level *L−4* square and
  its facts are the sample its parent keeps for it; a 128×128 page is 4,095 parent nodes whatever
  the world size. Below LOD 4 a page decodes the tiles it shows.
- **What changed between two moments** is a parallel descent of two roots that stops wherever the
  child refs are equal. Cost is proportional to the changed area, not to the world or to the
  history. The historical page cache uses it to drop only the pages that differ when the slider
  moves.
- **Unchanged tiles cost nothing.** A tile whose facts equal its current version is skipped; a
  commit that changes nothing writes a root and nothing else.

Two conscious departures from generation 1's principles: node samples are derived data that *is*
persisted and synced — deterministic copies of one cell, never summaries — and each commit is one
root, so time resolution is the broker's cadence, which was already the promise.

### The stack

```mermaid
flowchart LR
    Mod -- "observe(chunk, TileRecord)" --> Broker[ObservationBroker]
    Broker -- "commit(epoch, tiles)" --> Tree[MapTree<br/>persistent quadtree]
    Tree --> Segs[SegmentSet<br/>active-&lt;machine&gt;.pseg → &lt;sha256&gt;.pseg]
    Tree --> Blocks[BlockTable<br/>ids, frozen colors, foreign dictionaries]
    Broker -- latest view --> Builder
    Tree -- "tile / samples / changed" --> Builder[PageBuilder]
    Builder --> Shader[TerrainShader<br/>color · tint · slope · water]
    Shader --> Cache[MapPageCache] --> View[MapView] --> Screen
```

### Tile records — cost follows change

[TileCodec](https://github.com/fopwoc/GTNH-KNH/blob/2.2.2/mods/palimpsest/src/commonMain/kotlin/io/github/fopwoc/mods/palimpsest/tree/TileCodec.kt) writes each of the
four channels through [ChannelCodec](https://github.com/fopwoc/GTNH-KNH/blob/2.2.2/mods/palimpsest/src/commonMain/kotlin/io/github/fopwoc/mods/palimpsest/tree/ChannelCodec.kt),
which picks the smallest of: one value for the grid; a local palette with indices packed at
`ceil(log2 n)` bits; for full grids, residuals against the median-edge predictor of the west,
north and north-west neighbours (LOCO-I / JPEG-LS), packed at the width the largest residual
needs — heights are smooth, so mostly 0 and ±1; or raw. A delta record carries only the cells
that differ from its base through the same coder; a full record is forced after 16 deltas.

Each of those shapes also has a **range-coded** twin: the same indices or residuals through an
adaptive arithmetic coder (the LZMA-style range coder with per-context frequency models that
adapt identically on both sides, so nothing is stored but the bytes). Palette indices are
conditioned on the western neighbour's index; height residuals on how rough the already-decoded
neighbourhood is (four gradient contexts, an escape for the rare large jump). The encoder tries
every applicable shape and keeps the smallest, so a record never gets bigger for it.

| tile shape | bit-packed | range-coded (current) |
|---|---|---|
| uniform | 30 | 28 |
| near-uniform (one odd cell) | 65 | 38 |
| terrain bands (four block rows, height steps) | 161 | 49 |
| hills (three blocks, two biomes, rolling heights) | 196 | 67 |
| varied (noise in block and height) | 574 | 313 |

Bytes per tile include the tile's share of nodes. Plain encoding is 1,536 bytes per tile. On the
dense 512×512-tile world this took the segments from 113 MB to 38 MB, and the giant world's cold
generation from 187 MB to 54 MB; the edit histories, which are mostly nodes and tiny deltas,
barely moved. Encoding got slower — the four candidates are all built — commit p50 50 → 60 µs.

### Identical tiles are stored once

Every full record's facts are hashed (64 bits, epoch excluded) into a content index kept in a
primitive map in memory and written into each sealed segment's trailer. A tile whose facts match
a record already on disk — an ocean chunk, a desert, a farm restored to how it was — is written
as a **link**: its epoch, its previous version and the target, about ten bytes. The link is
preferred over a delta and a full record alike, and the target's facts are compared, not just the
hash, before linking. The synthetic worlds repeat their patterns, so they overstate it (the
512×512 world went from 38 MB to 17 MB, the giant world's cold generation from 54 MB to 32 MB);
in a real world the win is the water and the plains, which is most of the map. Commit p50 60 →
82 µs for the hash and the lookup.

### Segments — immutable, hashed, per machine

```mermaid
flowchart LR
    subgraph segment file
        H[header: magic, version, machine, ordinal] --> G1[group: records… + CRC] --> G2[group…] --> T[trailer: roots, machine slots<br/>sealed only]
    end
```

[SegmentWriter](https://github.com/fopwoc/GTNH-KNH/blob/2.2.2/mods/palimpsest/src/commonMain/kotlin/io/github/fopwoc/mods/palimpsest/tree/SegmentWriter.kt) stages a
commit's records into one CRC-framed group and appends it in a single write; readers on other
threads see a group entirely or not at all, and the whole active segment stays in memory so fresh
records never touch the disk. A torn tail is truncated on the next open. Sealing writes the
trailer, fsyncs, renames the file to its content hash and appends the name to
`segments.<machine>.txt`. Refs inside a file name other segments by *machine slot* and ordinal,
so a segment written after a git sync can point into another machine's files. Sealed segments are
memory-mapped on first use.

### Where the facts come from

The capture side is the only part that knows which game it runs in. Everything else above,
`MapTree` to `MapView`, is shared by GTNH 1.7.10, Fabric 26.2 and NeoForge 26.2.

```mermaid
flowchart LR
    Chunk[loaded chunk] --> Columns[ChunkColumns<br/>per platform]
    Columns --> Scanner[TileScanner<br/>KNH Core, common]
    Colors[BlockColors<br/>per platform] --> Vocab[BlockTable]
    Scanner -- "block, height, depth, biome" --> Record[TileRecord] --> Broker[ObservationBroker]
```

A platform scanner walks the loaded chunks around the player a few per tick, in a fixed spiral,
so every chunk in render distance is observed about every two seconds. `TileScanner` turns one
chunk into facts per column, looking down from the slice's ceiling: the first block the map does
not look through, its height, the depth of the water above it, and the biome. Water is looked
through to the floor. A *decoration* (a plant, a slab, a machine part: anything that isn't a full
cube) is the block but keeps the height of what it stands on, so a meadow doesn't shade like a
rockslide. Circuitry (torches, levers, redstone, rails) is see-through.

`BlockColors` gives each block its colour the first time the vocabulary sees it. Both platforms
average the block's top texture in linear light over its opaque texels, through KNH Core's shared
`TexelAverage`, and classify it as tinted by grass, by foliage or not at all. The texture comes
from where each game keeps it:

- **GTNH** asks the block for its icon at the position, which is how GregTech machines report
  their real texture from the tile entity, and decodes it from the resource manager. The
  vocabulary key is the block's name plus its dropped metadata, or a provider's stable identity:
  GregTech machines are keyed by what they are, not by metadata that also encodes transient
  state. Readiness providers hold back chunks whose tile-entity data hasn't arrived yet, since a
  chunk packet precedes its tile entities and a half-loaded GregTech machine stack would
  otherwise become history (AE2 blocks are guarded the same way).
- **26.2** reads the top-facing quads of the block state's baked model and decodes their
  textures; liquids use their still texture. A tint source whose colour differs between the
  block's default and its position is the biome's and is left to the shader; a constant one
  (spruce leaves) is baked in. The vocabulary key is the block's registry name: the flattening
  gave every material variant its own block, so the name is the identity. Biome tints are built
  from the level's biome registry and indexed by the same key hash the tiles store.

### Rendering — facts to pixels at page build

[PageBuilder](src/commonMain/kotlin/io/github/fopwoc/mods/palimpsest/render/PageBuilder.kt) fills a
129×129 grid of facts (a border row and column so slopes at the page edge see their neighbours)
from tiles or node samples, overlaying what the broker holds but hasn't committed yet;
[TerrainShader](src/commonMain/kotlin/io/github/fopwoc/mods/palimpsest/render/TerrainShader.kt)
turns it into RGBA: the block's frozen color, the biome tint where the block takes one, a
hillshade lit from the north-west against the western and northern neighbours (clamped, with a
checkerboard dither so one-block steps don't band), and water laid over the floor by depth,
see-through in the shallows and darker as it deepens. Every rule lives in the shader and none in
the history, so a better look repaints the past too.

### Why git can still be the replication protocol

Only sealed `.pseg` files, manifests and vocabularies are data; the active file and the machine id
are `.gitignore`d by the history store itself. Immutable segments can be combined by file union,
but that alone does not merge the meaning of independently continued root chains. Mutable
manifests and vocabularies also need a publication policy, and generation 2.4's current-region
files can have the same path with different contents after a fork. They cannot be treated as
conflict-free canonical history.

The intended replication model is sequential use on different installations: one writer at a
time, with the map directory synchronized between sessions. Production reads one machine's
history; two continuations made before synchronizing are not merged.

### What it measured, against generation 1

**Reads, where the tree wins.** The 48-tile viewport read 120 times, warm; "historical" is the
middle of the history.

| case | versions | gen 1 latest | gen 1 historical | gen 2 latest | gen 2 historical |
|---|---|---|---|---|---|
| sparse (8 cells / edit) | 33,024 | 36 µs | 232 µs | **75 µs** | **80 µs** |
| mixed (rectangles, scatter, full) | 33,024 | 35 µs | 328 µs | **26 µs** | **24 µs** |
| adversarial (1 cell / edit) | 33,024 | 35 µs | 45 µs | **12 µs** | **11 µs** |
| adversarial, 1 M versions | 1,001,024 | 44 µs | 85 µs | **14 µs** | **13 µs** |

A historical read costs the same as a live one because there is nothing to replay.

A page of a dense 512×512-tile world, cold in the same process, and from a fresh process:

| LOD | tiles under the page | gen 1 cold | gen 2 cold | gen 2 fresh process |
|---|---|---|---|---|
| 4 | 16,384 | 27–53 ms | **10.7 ms** | 13 ms |
| 5 | 65,536 | 58–114 ms | **6.7 ms** | 8.6 ms |
| 6 | 262,144 | 239–383 ms | **3.7 ms** | 10.7 ms |
| 7 | 1 M | 15–93 ms | **1.8 ms** | 9.1 ms |
| 8–12 | 4 M – 1 G | 13–39 ms | **1.2–1.8 ms** | 7.5–8.3 ms |

Giant world (1024×1024 tiles, an 8×8-tile base with 50,000 commits, reader and sealer alongside):

| | gen 1 | gen 2 |
|---|---|---|
| cold generation | 3.1 M layers in 27 s, 298 MB | 1.1 M records in 12 s, 193 MB |
| hot base commits | 100 epochs per append, p50 0.72 ms | one commit p50 **50 µs**, p99 130 µs |
| reader rebuilding the base page during writes | p50 0.42 ms | p50 **0.26 ms** |
| fresh process: open + base page | 11.6 ms | **0.5 ms** |
| base viewport (64 tiles), warm | 55 µs | **18 µs** |
| whole-world page (LOD 7), cold | 157 ms, 259 regions | **1.4 ms**, 5,436 nodes |
| time-lapse step, whole world | 169 µs | 320 µs |
| time-lapse step, base page | ~1 µs | 2.5 ms (64 tiles re-decoded) |

**Writes, where the tree pays.** Path copying costs about one node per level per changed
cluster. In the synthetic histories every commit changes 16 scattered tiles of a 32×32 world.
The first cut of the tree rewrote ~62 full nodes per commit (≈2.8 KB) on top of ~16 small
deltas; four changes then attacked that directly:

1. **One commit per interval, globally.** The broker used to commit each tile a minute after its
   last commit, so a tick could produce a root for whatever happened to be due — up to 60 roots a
   minute while exploring. Now every changed tile goes into one commit per minute, and far-zoom
   pages overlay the broker's uncommitted tiles so a new chunk still shows at once.
2. **Patch nodes.** A node that differs from its predecessor in one or two quarters is written
   as "that node, but these quarters" (~21 bytes instead of ~45); a full node is forced after
   eight patches so a cold read never follows a longer chain. 82 % of the nodes a commit writes
   are patches.
3. **Rooted squares.** The root names the smallest square holding everything seen, so the
   ~17 single-child levels above an explored world are not written at all; the tree grows a level
   only when an observation lands outside.
4. **Relative epochs and refs.** Epochs are deltas from the segment's base epoch (2 bytes, not 6);
   a ref into the segment being written costs one byte plus its offset.

| case | gen 1 on disk | gen 2, full nodes | gen 2, patch nodes | nodes per commit |
|---|---|---|---|---|
| sparse, 2,000 commits | 888 KB | 7.2 MB | **4.4 MB** | 45, 37 of them patches |
| mixed, 2,000 commits | 5.8 MB | 15.9 MB | **13.1 MB** | 45 |
| adversarial, 2,000 commits | 440 KB | 6.4 MB | **3.6 MB** | 45 |
| adversarial, 62,500 commits | 5.9 MB | 203 MB | **114 MB** | 45 |
| giant world hot base, 50,000 commits | ~8 MB | 129 MB | **74 MB** | 37, 32 of them patches |

What is left is a floor of about 1.8 KB per commit of 16 scattered tiles: ~60 records, each
paying its framing, a sample and a ref. It is per *commit*, not per tile, and in play a commit is
a minute of clustered changes, so a day of continuous building is ~1,440 commits of a few KB —
single-digit MB. The current unchanged-fact check skips idle periods without writing a root. History thinning (keeping hourly roots
after a month) would bound the long run if it ever matters.

Reopen is the root list: 2–4 ms for 2,000 roots, 26 ms for 62,500.

### Generation 2.4 — less bookkeeping, faster pages

By 2.3 the terrain itself was small: a full GTNH tile averaged about 170 bytes against 1,536 raw.
What remained was bookkeeping. In an editing-heavy map nodes took 68% of the bytes, and 97% of
the samples in node patches merely repeated their base. 2.4 went after that overhead, and after
the page pipeline.

**Smaller records** (segment format 4; older maps are archived, not converted):

- node patches write only the quarters and samples that changed and inherit the rest from the
  base they decode anyway
- tile deltas carry only the channels that changed
- references to nearby records are short backward distances
- a full record replaces a delta whenever it comes out smaller; checkpoints stay one chunk each

**Current tiles in regions.** The latest look of each tile used to be one raw 1.5 KB file per
chunk, forced to disk one by one. It moved into compressed 32×32-chunk region files with an
in-memory index, flushed in batches. Replacing one tile 180 times went from 1,024 files and
4 MB allocated to one file and 92 KB, and the first durable write from 4.8 s to 23 ms.

**Faster pages.**

- a viewport resolves all its tile addresses in one tree walk, cached per root, with no extra
  bytes on disk
- the root index is paged, so appending a root copies one page instead of the whole list
- a page keeps its last sample grid, and an edit re-reads and re-shades only the tiles it touched
- the map and the minimap share a 24 MiB budget for those grids, one packed long per sample

**What it measured** (synthetic fixtures on the same M4 Max):

| | before | after |
|---|---:|---:|
| History, sparse edits, 2,000 commits | 4.4 MB | 3.2 MB |
| History, one pixel per tile, 2,000 commits | 3.3 MB | 2.1 MB |
| Giant world after 50,000 hot commits | 111 MB | 83 MB |
| Warm 64-tile historical read, p50 | 4.8 µs | 0.8 µs |
| Sparse page update, million-tile world, p50 | 253 µs | 23 µs |
| Appending 25,000 roots | 141 ms | 1.9 ms |

**What it cost.**

- cold coarse pages got a little slower, because inherited samples have to be rebuilt from patch
  bases (a first LOD 12 build went from 3.2 to 5.1 ms)
- every sealed file is now checked against its SHA-256 name on first open; a million-version
  history opened in 44 ms instead of 28, until decoding segments in parallel brought a 13-segment
  map down to 15 ms
- dense page rebuilds did not get faster; the incremental path only helps sparse edits

The fixtures repeat terrain patterns on purpose to exercise content reuse, so their sizes should
not be extrapolated to noisy real terrain.

### Where the ideas come from

- Path-copying persistent trees, structural sharing, "a commit is a root": **git**, Clojure's
  persistent vectors, Datomic, ZFS/Btrfs copy-on-write.
- Samples in internal nodes so a coarse view reads coarse nodes: **mipmaps**, quadtree LOD in
  terrain engines.
- Per-record adaptive shapes and the median-edge predictor: **JPEG-LS / LOCO-I**, PNG filters.
- Append-only CRC-framed groups, immutable content-addressed files, merge by union: **LSM trees**,
  git, Perkeep — inherited from generation 1.

### Guarantees and limits

- **Durability:** sealed segments were fsynced; the active one was written per commit but not
  fsynced, so a crash lost the last commits, which the scanner then saw again.
- **Integrity:** a sealed file was checked against its SHA-256 name before its first read.
- **Concurrency:** one commit at a time from the game thread, parallel reads of immutable records,
  and a lock so a second game could not write the same map.
- **Bounds:** at most 16 deltas per tile and 8 patches per node to decode; 16-bit block ids per
  machine.
- **Never done:** packed snapshots for cold sequential reads, reading several machines' histories
  at once, history thinning.

---

## Generation 3 — the world in 3D

### Why the quadtree had to go

Generation 2 stored what the map draws: one top-down layer of facts per tile. That made the map
fast and the history small, and it also meant the history could never show anything the top-down
layer hides. A cave, the floors of a base, the room the minimap looks down from: each needed a
separate slice, filled only while the player happened to stand under that ceiling. And the more the
mod wanted to show, the more slices the same chunk would be written into.

Generation 3 turns this around. The history keeps what the client actually received, whole chunks
in 3D, and everything the map draws is derived from it. The surface is one such view, a ceiling at
any height is another, and a future 3D view of a base will be a third. None of them is stored as
truth, so adding a view never means migrating history.

### Truth and views

The truth is append-only. Each game session writes its own files: a manifest that names what the
session committed, a vocabulary of block identities, and per dimension a segment of commit frames.
A commit is one moment at a world tick. Its frame holds the chunks that changed since the previous
moment; a chunk that looks the same costs nothing, and a section that changed is usually stored
as a delta against its previous version, range-coded, with chains kept short so reading any
moment stays bounded. Sessions only ever add files, which is what makes the folder safe to
synchronise like a save game.

Views live in a local cache next to the game, never in the synced folder: per region, every
version of every chunk with its encoded surface; a coarse overview for far zoom; the timeline of
moments. They are rebuilt from the truth whenever they are missing, stale or from another machine,
and caught up incrementally when only the tail is new. Losing the cache costs a rebuild, never
history.

Each dimension keeps either its full history or only its latest state, and compaction merges
segments in the background when they pile up.

### Sync without merge

Generation 2 wanted git: per-machine segments, a union merge, foreign vocabularies translated on
read. Generation 3 gives that up on purpose. One computer plays at a time; the folder travels
between them like a save game; each session continues from the last one it finds. If two computers
did continue the same history, the database notices two heads and refuses to guess: one branch is
kept and the other archived, never mixed. A folder caught mid-synchronisation, with a manifest
naming files that have not arrived, is not opened at all.

### What it measured

A real long-lived 1.7.10 server world, fed in 256 chunks per commit, on the same 14-core M4 Max
with JDK 27. Spotlight was indexing on one core throughout, so these are not best-case numbers.

| | Overworld, 315,643 chunks | Nether, 64,396 chunks |
|---|---:|---:|
| History on disk | 715 MiB, 2.3 KiB per chunk | 144 MiB, 2.3 KiB per chunk |
| Local indexes | 113 MiB | |
| Commit of 256 new chunks, p50 / p95 | 9.8 / 13.1 ms | 10.6 / 14.6 ms |
| Revisit of 256 unchanged chunks, p50 | 1.0–1.2 ms | 1.7 ms |
| Reopen with warm indexes | 15 ms, 0.6 MiB of heap | 7 ms |
| Reopen that compacts to latest-only and remaps indexes | 2.7 s | |
| Rebuilding every index from scratch | 12.2–12.5 s | |
| Surface of a 33×33-chunk window, warm | 1.4–1.5 ms | 1.1 ms |
| Ceiling at y = 40 of the same window, warm | 1.2–1.5 ms | 1.8 ms |
| Whole world for far zoom (432×293 cells), warm | 1.1–1.4 ms | |
| Changes between two moments, 40×40-chunk window | 1.7–3.1 ms | |
| One step of time-lapse playback | 0.2 ms | |

Commits scale with the background pool: exploring at three new chunks a commit costs 21 ms on one
thread and 2.2 ms on fourteen.

On synthetic workloads, section deltas made history 7–13 times smaller than storing changed
sections whole.

### What the map does with it today

The map still draws surfaces only: the live view, history and far zoom read the surface and
overview views, and the minimap keeps its own short-lived slices in memory. The scanner stages a
full snapshot of every loaded chunk about once per commit interval, and the database turns what
changed into the next moment. Block colours are a local cache too, kept next to the indexes,
because every machine sees blocks through its own resource packs.

