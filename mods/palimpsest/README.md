# Palimpsest

A world map that remembers everything it has ever seen: not just what the world looks like now, but what it looked like at every moment you were there. Scroll back through history and watch a base grow, a forest get cleared, a river get dammed.

Client-side only. It maps what your client sees, so it works on any server.

![palimpsest1.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/palimpsest1.png)
![palimpsest2.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/palimpsest2.png)
## Features

- maps the chunks around you as you play, a few per tick, with no pause and no setup
- a top-down map with relief shading, biome-tinted grass, leaves and water, and water that gets deeper and darker over the seabed
- smooth panning and zooming, from single blocks out to whole regions
- a square minimap in a corner of the screen
- **History**: step through every snapshot, and the map flies to what changed and flashes it
- colours come from your resource pack, but are frozen the first time a block is seen, so a pack change never repaints the past
- GregTech machines show as the machine, not a generic casing, and machines still loading their data never show up as false changes in history
- maps are plain files meant to live in a git repository, so several instances or friends can merge their maps

## Install

Install Palimpsest together with [KNH Core](https://github.com/fopwoc/GTNH-KNH/tree/main/framework) of the same version. Supported loaders and Minecraft versions, and what else to install, are listed in the [main README](https://github.com/fopwoc/GTNH-KNH#install).

## Use

Press **M** (rebindable under Controls) or run `/palimpsest` to open the map. An arrow marks where you are and look, and while the map is live, the same dots as on the minimap show what's around you.

| Action | Control |
| --- | --- |
| Pan | drag, or WASD / arrow keys |
| Zoom around the cursor | mouse wheel, or + and - |
| Back to the player | Home |
| Browse history | **History**, then the wheel over the list or a click on a snapshot |
| Back to now | **Back to live** |

### Waypoints

Use **+ Waypoint** to mark your current position, or right-click the live map to mark a place on it. Click an existing marker or open **Waypoints** to edit its name, coordinates, and icon, or delete it. New waypoints use a compass icon; **Use held item** changes it to the item you are holding. Waypoints appear on the full map and minimap. **Show in world** adds an icon and distance indicator to first-person play, with a directional turn cue when the waypoint is off screen; the eight nearest tracked waypoints are shown. World markers hide with the HUD (F1). Waypoints are saved per world and dimension, separately from terrain history, and are hidden while browsing old snapshots.

### Visual Prospecting

With Visual Prospecting installed on GTNH, discovered ore veins and underground fluids appear as separate layers on the live full map, minimap, and first-person HUD. With TCNodeTracker installed, scanned Thaumcraft aura nodes appear as a third layer on the same surfaces. Use **Ores**, **Fluids**, and **Nodes** on the full map to show or hide each available layer everywhere. These choices last until the game closes. The HUD shows up to six nearby discoveries within 256 blocks; hovering a map marker shows its details and coordinates. Palimpsest reads the owning mods' client data and never saves copies of their discoveries as waypoints.

### ServerUtilities claims

With ServerUtilities installed on GTNH, claimed chunks are tinted by team color on the live full map and minimap. A strong chunk border means ServerUtilities reported it as force loaded. Hover a chunk to see its owner and loading status. Use **Claims** on the full map to toggle the layer on both maps. ServerUtilities may hide another team's loading status from players without permission, so a faint border means the chunk is either not force loaded or its status is hidden. Palimpsest requests visible claim windows from ServerUtilities and keeps them only in memory.

### Minimap

A square minimap of the live map around you sits in a corner of the screen, with an arrow showing where you look: north stays up and the arrow turns, or, if you prefer, the arrow stays up and the map turns under it. A turning map shows a small N badge on its edge where north lies. Hold **Z** for a big see-through map over most of the screen; it turns the same way as the minimap. Both show what's around you right now as dots: red for dropped items, orange for hostile mobs, green for other mobs and white for other players, faint when they're more than a few blocks above or below you. The dots are never saved. Both hide with the HUD (F1).

| Action | Control |
| --- | --- |
| Show or hide | Unbound by default; assign a key in Controls |
| Zoom in and out | **=** and **-** |
| Big map, while held | **Z** |

Showing it and its zoom last until you close the game; the defaults are settings.

Commands:

- `/palimpsest flush` saves what's been seen right away instead of waiting for the next commit
- `/palimpsest where` prints the map's folder
- `/palimpsest stats` shows how much history the map holds and how big it is on disk
- `/palimpsest block` explains how the map sees the blocks under your feet
- `/palimpsest bench` opens a storage benchmark on a synthetic world

## Settings

In the loader's config screen, or in `config/palimpsest.cfg` or `config/palimpsest.toml`, depending on the loader:

- **Commit interval**: how often what you've seen becomes history, 60 seconds by default. Shorter gives a finer time-lapse and uses more disk.
- **Minimap**: whether it starts shown, its corner (top right by default), horizontal and vertical padding from the screen edges (16 GUI pixels each by default), its size in GUI pixels (100 by default), whether it keeps north up (the default) or turns with you, whether your coordinates show under it, and which dots it shows: items, mobs and players, all on by default.
- **Big map opacity**: how see-through the big map is, 70% opaque by default.

## Where maps live

`<instance>/palimpsest/maps/<world>/<dimension>/`, one folder per world or server and per dimension. Put `palimpsest/maps/` under git to share it. Each installation writes only its own files, so merging two machines' maps is a plain union of files with no conflicts.

## For developers

```bash
./gradlew :palimpsest:buildAll
./gradlew :palimpsest:storageSuite
```

The storage, history, rendering and map screen are shared in `src/commonMain`. Chunk scanning and block colours live in the per-platform source sets. `storageSuite` runs the headless storage benchmark without the game and saves its report under `build/palimpsest/reports/`. How the storage works and why is in [ARCHITECTURE.md](https://github.com/fopwoc/GTNH-KNH/blob/main/mods/palimpsest/ARCHITECTURE.md).
