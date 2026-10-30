# palimpsest-inspect

A command line for reading a Palimpsest map history when something on the map looks wrong: which commits recorded what, and whether a change is real.

It never opens the folder you point it at. Opening a history starts a session that writes into it, so the tool copies the history (without its lock) and the cache into a temporary directory, works on the copy and deletes it. That makes it safe on the world of a game that is running.

## Run

```bash
./gradlew :palimpsest-inspect:installDist
```

The launcher is `tools/palimpsest-inspect/build/install/palimpsest-inspect/bin/palimpsest-inspect`. Histories live in the game folder as `palimpsest/maps/<world>/history`, their caches as `palimpsest/cache/<world>`. Passing `--cache` is optional; without it the indexes are rebuilt, which takes longer on a big world.

| Command | What it shows |
|---|---|
| `dimensions <history>` | Every dimension with its commit count and time span |
| `commits <history> -d dim0` | Each commit: tick, time, chunks, sections and bytes written |
| `changes <history> -d dim0` | Every change between neighbouring commits, sorted: nothing different, biome only, whole sections emptied or filled, losses later undone exactly, which blocks turned into which, identities that kept their block (metadata, GregTech machine details) and what changed from above |
| `chunk <history> -d dim0 <x> <z>` | Every distinct version of one chunk: blocks per section and the most common blocks |

`--top` sets how many rows the lists of `changes` print. The database's own log goes to stderr; `--log-level` or `LOG_LEVEL` sets it, `error` by default.

A loss that is later undone exactly is the usual sign of a chunk seen half loaded or half unloaded, but moving a region away and back, or restoring a backup, looks the same.
