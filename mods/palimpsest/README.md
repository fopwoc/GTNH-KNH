# Palimpsest

A world map that remembers everything it has ever seen: not just what the world looks like now, but what it looked like at every moment you were there. Scroll back through history and watch a base grow, a forest get cleared, a river get dammed.

Client-side only: GT New Horizons 1.7.10, Fabric 26.2 and NeoForge 26.2. It maps what your client sees, so it works on any server.

## Features

- maps the chunks around you as you play, a few per tick, with no pause and no setup
- a top-down map with relief shading, biome-tinted grass, leaves and water, and water that gets deeper and darker over the seabed
- smooth panning and zooming, from single blocks out to whole regions
- **History**: step through every snapshot, and the map flies to what changed and flashes it
- colours come from your resource pack, but are frozen the first time a block is seen, so a pack change never repaints the past
- on GTNH, GregTech machines show as the machine, not a generic casing, and machines still loading their data never show up as false changes in history
- maps are plain files meant to live in a git repository, so several instances or friends can merge their maps

## Install

Install Palimpsest and [KNH Core](../../framework/) for the same loader and version.

- **GTNH:** nothing else, Forgelin is part of the pack
- **Fabric:** Fabric API, Fabric Language Kotlin, Forge Config API Port
- **NeoForge:** Kotlin for Forge

## Use

Press **M** (rebindable under Controls) or run `/palimpsest` to open the map.

| Action | Control |
| --- | --- |
| Pan | drag, or WASD / arrow keys |
| Zoom around the cursor | mouse wheel, or + and - |
| Back to the player | Home |
| Browse history | **History**, then the wheel over the list or a click on a snapshot |
| Back to now | **Back to live** |

Commands:

- `/palimpsest flush` saves what's been seen right away instead of waiting for the next commit
- `/palimpsest where` prints the map's folder
- `/palimpsest stats` shows how much history the map holds and how big it is on disk
- `/palimpsest block` explains how the map sees the blocks under your feet
- `/palimpsest bench` opens a storage benchmark on a synthetic world

## Settings

In the loader's config screen, or in `config/palimpsest.cfg` (GTNH) or `config/palimpsest.toml` (Fabric, NeoForge):

- **Commit interval**: how often what you've seen becomes history, 60 seconds by default. Shorter gives a finer time-lapse and uses more disk.

## Where maps live

`<instance>/palimpsest/maps/<world>/<dimension>/`, one folder per world or server and per dimension. Put `palimpsest/maps/` under git to share it. Each installation writes only its own files, so merging two machines' maps is a plain union of files with no conflicts.

## For developers

```bash
./gradlew :palimpsest:buildAll
./gradlew :palimpsest:storageSuite
```

The storage, history, rendering and map screen are shared in `src/commonMain`. Chunk scanning and block colours live in `src/gtnhMain` and `src/modernMain`. `storageSuite` runs the headless storage benchmark without the game and saves its report under `build/palimpsest/reports/`. How the storage works and why is in [ARCHITECTURE.md](ARCHITECTURE.md).
