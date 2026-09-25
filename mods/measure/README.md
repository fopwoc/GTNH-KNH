# Measure

A measuring tape for Minecraft. Place anchors on blocks or in mid-air and get lines, boxes and spheres drawn in the world with their sizes. They're kept per world, and you can export a design and rebuild it somewhere else.

Client-side only: GT New Horizons 1.7.10, Fabric 26.2 and NeoForge 26.2. Nothing is needed on the server.

![measure1.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/measure1.png)
![measure2.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/measure2.png)
![measure3.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/measure3.png)

## Features

- **Line**, **Area** and **Sphere** measurements, with lengths and block counts written in the world
- areas and spheres are translucent glass; from inside, a sphere shows a grid and a ring at eye height, which is the layer to build when placing it block by block
- anchors behind terrain show through as ghosts, and the parts of a sphere that go underground are drawn fainter
- hold Shift to constrain: lines and radii snap to an axis, areas become cubes
- select, move, resize, copy, cut, paste, delete, undo and redo
- export a set to a file, import it in another world, and move the whole batch to where you're looking: design in creative, build on the server
- F1 hides the tools and keeps only the shapes
- works from a detached camera such as Freecam, with longer reach you can adjust
- macOS shortcuts use Cmd and Option, detected automatically

## Install

Install Measure and [KNH Core](../../framework/) for the same loader and version.

- **GTNH:** nothing else, Forgelin is part of the pack
- **Fabric:** Fabric API, Fabric Language Kotlin, Forge Config API Port
- **NeoForge:** Kotlin for Forge

## Use

Open the menu with `/measure`, or bind **Open measure menu** under Controls (unbound by default). Pick a mode there. The menu also lists the measurements of the current dimension for selecting, undo and redo, export, import and Move.

With a mode active, aim and:

| Action | Windows / Linux | macOS |
| --- | --- | --- |
| Place an anchor | Middle mouse | Middle mouse |
| Place on the adjacent face | Ctrl + middle mouse | Control + middle mouse |
| Select a measurement | Shift + middle mouse | Shift + middle mouse |
| Add to the selection | Shift + Ctrl + middle mouse | Shift + Control + middle mouse |
| Move or resize | Alt + middle mouse | Option + middle mouse |
| Constrain to an axis or cube | hold Shift | hold Shift |
| Copy, cut, paste | Ctrl + C, X, V | Cmd + C, X, V |
| Undo, redo | Ctrl + Z, Ctrl + Y | Cmd + Z, Cmd + Shift + Z |
| Delete the selection | Delete or Backspace | Delete |
| Cancel | Escape | Escape |
| Adjust reach from a detached camera | Ctrl + scroll | Cmd + scroll |

Hold Shift while adjusting reach for steps of 8. A hint box above the hotbar shows what you can do right now.

### Sharing designs

- `/measure export <name>` saves the selection, or the whole dimension if nothing is selected
- `/measure import <name>` adds a saved set to the current dimension and selects it; undo takes it back
- `/measure exports` lists saved sets
- `/measure move`, or **Move** in the menu, picks the selection up; its lowest corner follows your aim, and a place click drops it

## Settings

In the loader's config screen, or in `config/measure.cfg` (GTNH) or `config/measure.toml` (Fabric, NeoForge): sphere grid, sphere radius lines, the hint box and its position, the shortcut scheme, undo history size and detached-camera reach.

Measurements are saved in `config/measure/measurements/`, one file per world or server. Exports go to `config/measure/exports/`.

## For developers

```bash
./gradlew :measure:buildAll
```

Jars for every loader land in `mods/measure/build/libs/`. The measurement model, overlay scene and menus are shared in `src/commonMain`. The in-world drawing and input for 26.2 live in `src/modernMain`, and for 1.7.10 in `src/gtnhMain`.
