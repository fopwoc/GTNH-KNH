# Measure

Client-side measuring tape for GT New Horizons (Minecraft 1.7.10). Place anchors on blocks or in the air, get lines, boxes and spheres with their sizes drawn right in the world, and keep them between sessions.

![measure1.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/measure1.png)
![measure2.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/measure2.png)

## What it does

- **Line**, **Area** (box) and **Sphere** measurements with block counts and lengths rendered on the overlay
- anchors go on block faces, on the adjacent face with Ctrl, or on existing anchors floating in mid-air
- Shift snaps placement to right angles
- select one or many measurements, move and resize them, copy / cut / paste, delete, undo / redo
- measurements are saved per world / per server and come back next time you join
- export a set to a file, import it in another world, then **Move** the whole batch to where you are looking — design in creative, place on the server
- works in [Freecam](https://github.com/GTNewHorizons/Freecam): the camera is the viewer, reach is extended (default 32 blocks) and adjustable with Ctrl/Cmd + scroll (Shift = steps of 8)
- Mac-friendly shortcuts (Cmd instead of Ctrl), detected automatically

Nothing is sent to or required on the server.

## Install

Drop `measure-<version>.jar` and the matching `knh-core-<version>.jar` into `mods/`. Needs Forgelin (already part of GTNH).

Versions of Measure and KNH Core must match.

## Use

`/measure` opens the menu — or bind **Open measure menu** under Options → Controls → Measure (unbound by default). Pick a mode there, browse and select measurements of the current dimension, undo/redo, export/import, Move.

With a mode active, aim and:

| Action | Windows/Linux | macOS |
| --- | --- | --- |
| Place an anchor | Middle mouse | Middle mouse |
| Place on the adjacent block face | Ctrl + middle mouse | Control + middle mouse |
| Select a measurement | Shift + middle mouse | Shift + middle mouse |
| Add to selection | Shift + Ctrl + middle mouse | Shift + Control + middle mouse |
| Move / resize | Alt + middle mouse | Option + middle mouse |
| Right-angle snap | hold Shift | hold Shift |
| Copy / cut / paste | Ctrl+C / X / V | Cmd+C / X / V |
| Undo / redo | Ctrl+Z / Ctrl+Y | Cmd+Z / Cmd+Shift+Z |
| Delete selection | Delete / Backspace | Delete |
| Cancel | Escape | Escape |

The measurement you are looking at gets a thicker outline when it is ready to be selected. A hint box above the hotbar lists what you can do with the current selection; the menu shows the full reference.

### Sharing measurement sets

- `/measure export <name>` — writes the selection (or everything in the dimension if nothing is selected) to `config/measure/exports/<name>.json`
- `/measure import <name>` — merges it into the current dimension and leaves the imported measurements selected (undoable, duplicates skipped)
- `/measure exports` — lists available files
- **Move** (menu button or `/measure move`) — picks the selection up as one batch; its lowest corner follows the crosshair, Shift locks it to an axis, the place click drops it
- Cmd/Ctrl+A in the menu selects everything in the list

The same export/import/Move controls are in the menu.

## Settings

**Mods → Measure → Config** or `config/measure.cfg`: area and sphere style (wire lines, translucent glass, or both), hint box on/off and its margin, shortcut scheme (auto / standard / macOS), undo history size, freecam reach.

Saved measurements live in `config/measure/measurements/`, one file per world or server address.

## Build

```bash
./gradlew -p framework publishToMavenLocal
./gradlew -p mods/measure clean build
```

Jar: `mods/measure/build/libs/measure-<version>.jar`. See the [repository README](../../README.md) for the full build.
