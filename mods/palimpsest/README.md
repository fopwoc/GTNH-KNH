# Palimpsest

How the storage works, why, and what it measured: [ARCHITECTURE.md](ARCHITECTURE.md).

Palimpsest is a client-side world map that remembers everything it has ever seen: not only what the map looks like now, but what it looked like at any moment, so a long world can be scrubbed like a time-lapse. Its data directory is meant to live in a git repository shared between your instances.

Press **M** (rebindable) or run `/palimpsest` for the map: drag or WASD/arrows to pan, wheel or +/- to zoom around the cursor, Home to jump to the player, and the bar at the bottom scrubs time; **Live** returns to now. While a world is open the mod scans the loaded chunks around you a few per tick and observes each about every two seconds; observations become history at most once a minute per chunk. `/palimpsest flush` commits pending observations, `/palimpsest where` prints the directory, `/palimpsest block` explains how the map classifies the block under you, `/palimpsest bench` opens the storage benchmark showcase.

## What is stored

Data lives in `<instance>/palimpsest/maps/<world>/dim<N>/`:

- `blocks.<machine>.tsv` — the block vocabulary of one machine: `mod:block:meta`, its id, the color and tint flag frozen the first time that machine saw the block. Only the owning machine appends to its file, so two machines never conflict; everyone else reads it to translate that machine's records. A resource-pack change never repaints old history; blocks seen for the first time take the current pack's color.
- `y255/` — the surface map: the history looked down from Y=255. A cave slice would be another directory at its ceiling, sharing the vocabulary.
- inside a slice: content-addressed `<sha256>.pseg` segments, one `segments.<machine>.txt` manifest per machine (ordinal → segment name), a `created` timestamp, and a `.gitignore` for the local `active-<machine>.pseg` being written.
- `.machine` — a random id for this installation, local, never synced.

A tile (one chunk seen from above) stores **facts**, not colors: per block the vocabulary id of the first block the map does not look through, its height, the liquid depth above the floor, and the biome. Only full opaque cubes and liquids count as a surface; slabs, stairs, fences, plants and glass let the map read the block underneath. Colors are computed when a page is drawn — the frozen block color, the biome's grass tint for blocks the game tints, the vanilla map's slope and water shading — so the look can improve without touching history.

## The storage in one paragraph

The map is a persistent quadtree over tiles. Every commit copies the path from the changed tiles up to a new root and shares every other node with the previous root; each node keeps one sample pixel per child, so a zoomed-out page reads a few thousand nodes and never opens a tile; time travel is picking the root in force at that moment; and "what changed between two moments" is a walk that descends only where the two trees stop sharing. Tile records pay for how much they vary: a solid tile is a few bytes, gentle terrain a couple of hundred, and an edit is a delta against the previous version. Records land in append-only segments that are sealed at 4 MiB and renamed to their content hash, which is why a git merge of two machines' directories is a plain union of files.

## Benchmarks

`/palimpsest bench` opens an in-game showcase over a synthetic 32×32-tile world: **Sparse +250**, **Mixed +250** and **Stress +1000** append reproducible edit histories, the slider scrubs time, **Paged view** renders the same history through the map's page pipeline at any zoom, **Probe 120 reads** reports read latency percentiles, **Seal segment** times a seal.

The headless suite, `./gradlew :palimpsest:storageSuite`, needs no game: bytes per tile for map-like tile shapes; sparse, mixed and adversarial edit histories including a million-tile-version case, with pixel digests checked across reopen; a dense 512×512-tile world plus distant observations, timing a page at every zoom level cold, warm, historical and from a fresh process; and a giant 1024×1024-tile world with an 8×8-tile base edited 50,000 times while a reader and a sealer run alongside, followed by a fresh open and a ten-step time-lapse. The report is printed and saved under `build/palimpsest/reports/`. It lives in the `benchmark` source set and is not part of the mod jar. It measures the storage on your machine; it is not a gameplay performance result.
