# Palimpsest

A world map that remembers every moment it has seen. Scroll back and watch a base grow, a forest get cleared, a river get dammed.

Client-side only: it maps what your client sees, on any server.

![palimpsest1.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/palimpsest1.png)
![palimpsest2.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/palimpsest2.png)

## Features

- maps the chunks around you as you play, no setup
- relief shading, biome-tinted grass, leaves and water, deeper water drawn darker
- smooth zoom from single blocks out to whole regions
- **History**: step through snapshots; the map flies to what changed and flashes it
- a minimap that shows the room you're in when you're indoors
- waypoints on the map, the minimap and in the world
- colours come from your resource pack and are frozen on first sight, so a new pack never repaints the past
- GregTech machines show as the machine, not a generic casing
- history keeps whole chunks in 3D, not just the view from above
- move a world's history between computers by syncing one folder

## Install

Install Palimpsest together with [KNH Core](https://github.com/fopwoc/GTNH-KNH/tree/main/framework) of the same version. Supported loaders and Minecraft versions, and what else to install, are listed in the [main README](https://github.com/fopwoc/GTNH-KNH#install).

## Map

Press **M** or run `/palimpsest`.

| Action | Control |
| --- | --- |
| Pan | drag, or WASD / arrow keys |
| Zoom around the cursor | mouse wheel, or + and - |
| Back to the player | Home |
| Browse history | **History**, then the wheel over the list or a click on a snapshot |
| Back to now | **Back to live** |
| Add a waypoint | **+ Waypoint** for where you stand, or right-click the map |
| Edit waypoints | click a marker, or **Waypoints** |

Waypoints are kept per world and dimension. **Show in world** puts a marker with the distance on screen, for the eight nearest.

## Minimap

Shows the surface outside and the room you're in indoors. Dots mark what's around you right now: red items, orange hostile mobs, green other mobs, white players.

| Action | Control |
| --- | --- |
| Show or hide | unbound; assign a key in Controls |
| Zoom | **=** and **-** |
| Big see-through map, while held | **Z** |

## Integrations

On GTNH, with these mods installed:

- **Visual Prospecting**: ore veins and underground fluids on the map, minimap and HUD
- **TCNodeTracker**: scanned aura nodes as another layer
- **ServerUtilities**: claimed chunks tinted by team colour; a bold border means force-loaded

Toggle each layer with **Ores**, **Fluids**, **Nodes** and **Claims** on the map. Ores, fluids and claims start hidden each time the game starts.

## Commands

- `/palimpsest flush`: save what you've seen now instead of at the next commit
- `/palimpsest where`: print the map's folder
- `/palimpsest stats`: how many moments the history holds and what's waiting to be saved
- `/palimpsest block`: how the map sees the blocks under your feet

## Settings

In the loader's config screen, or `config/palimpsest.cfg` / `config/palimpsest.toml`:

- **Commit interval**: how often what you see becomes history, 60 s by default; shorter is a finer time-lapse and more disk
- **Minimap**: shown at start, corner, padding, size, north-up or turning with you, coordinates, which dots
- **Big map opacity**: 70% by default
- **Waypoints in the world**: on by default

## Where maps live

Under `<instance>/palimpsest/`, per world or server:

- `maps/<world>/history/`: the history of every dimension
- `maps/<world>/<dimension>/`: waypoints
- `cache/<world>/`: indexes and block colours, rebuilt from the history; safe to delete

To move a map to another computer, sync `maps/` after closing the world and play on one computer at a time. Maps from Palimpsest 2.x aren't read.

## For developers

```bash
./gradlew :palimpsest:buildAll
```

History is stored by the `palimpsest-db` library in `lib/palimpsest-db`; the map, its screens and the glue to the library are in `src/commonMain`, chunk scanning and block colours in the per-platform source sets. How the storage works and why: [ARCHITECTURE.md](https://github.com/fopwoc/GTNH-KNH/blob/main/mods/palimpsest/ARCHITECTURE.md).
