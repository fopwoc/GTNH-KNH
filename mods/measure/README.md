# Measure

Measure is a client-side measurement toolkit for GT New Horizons. It creates persistent line and area selections and renders them as in-world overlays.

## Features

- line and area measurement modes
- live placement previews and right-angle constraints
- single and multi-selection
- move and resize interactions
- copy, cut, paste, delete, undo, and redo
- separate persisted measurements for each singleplayer world or multiplayer server
- a Compose Runtime-based editor screen

## Requirements

- GT New Horizons 2.8 / Minecraft 1.7.10
- Forgelin
- [KNH Core](../../framework/) with the same version as Measure

Measure is client-side and does not need to be installed on the server.

## Usage

Run `/measure` to open the editor and choose a measurement mode. While a mode is active, aim at blocks and use the following controls:

| Action | Windows/Linux | macOS |
| --- | --- | --- |
| Create or place an anchor | Middle mouse button | Middle mouse button |
| Target the adjacent block face | Ctrl + middle mouse | Control + middle mouse |
| Select an existing measurement | Shift + middle mouse | Shift + middle mouse |
| Add to selection | Shift + Ctrl + middle mouse | Shift + Control + middle mouse |
| Move or resize | Alt + middle mouse | Option + middle mouse |
| Constrain placement to right angles | Hold Shift | Hold Shift |
| Copy / cut / paste | Ctrl+C / Ctrl+X / Ctrl+V | Command+C / Command+X / Command+V |
| Undo | Ctrl+Z | Command+Z |
| Redo | Ctrl+Y or Ctrl+Shift+Z | Command+Shift+Z |
| Delete selection | Delete or Backspace | Delete |
| Cancel current interaction | Escape | Escape |

The editor displays the active platform-specific shortcuts in its footer.

## Saved data

Measurements are saved as JSON files under:

```text
<instance>/config/measure/measurements/
```

File names are derived from the server address or singleplayer world name. Measurement data never needs to be installed on the server.

## Build

From the repository root:

```bash
./gradlew -p framework publishToMavenLocal
./gradlew -p mods/measure clean build
```

Artifact:

```text
mods/measure/build/libs/measure-<version>.jar
```
