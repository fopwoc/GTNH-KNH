# TPS Tab

Client-side Kotlin Forge mod for GT New Horizons 2.8 on Minecraft 1.7.10.

Project layout follows the standard GTNH single-mod structure, with sources in `src/main/kotlin` and resources in `src/main/resources`.

## What it does

- Reads OPIS-published TPS/MSPT text already exposed to the client through the tab list or scoreboard.
- Avoids chat command spam and any fallback estimation path.
- Renders overall TPS/MSPT and current-dimension TPS/MSPT directly under the tab player list.

The overlay config is stored in:

```text
<instance>/config/tab_tps.json
```

The overlay polls that JSON file on client ticks, so edits to `enabled`, `staleDataTicks`, `showPlaceholder`, and `placeholderText` hot-reload without restarting the game.

Key settings:

- `enabled`
- `staleDataTicks`
- `showPlaceholder`
- `placeholderText`

By default the placeholder text is `Waiting for OPIS TPS data...` until OPIS lines are visible client-side.

## Requirements

- GT New Horizons 2.8 / Minecraft 1.7.10
- `Forgelin` present in the modpack
- `KNH Core` (`knh-core`) present in the modpack
- Java 25 for the Gradle build runtime

## Build this module locally

```bash
../../gradlew -p . build
```

Expected artifact:

```text
build/libs/
```

## Build with the root orchestrator

```bash
../../build.sh
```

