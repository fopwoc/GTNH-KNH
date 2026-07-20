# TPS Tab

TPS Tab is a client-side GT New Horizons mod that adds server performance information below the multiplayer tab list.

## How it works

TPS Tab passively looks for TPS/MSPT text already exposed through the player list or scoreboard. When an overall TPS line is unavailable, it estimates TPS from vanilla world-time synchronization packets.

It does not request privileged server data, run server commands, or need a server-side companion mod. A time-sync estimate is inherently less authoritative than TPS values published by the server.

## Features

- overall TPS and MSPT below the tab list
- current-dimension TPS and MSPT when the server publishes them
- passive fallback based on world-time packets
- stale-data indication
- hot-reloadable JSON configuration

## Requirements

- GT New Horizons 2.8 / Minecraft 1.7.10
- Forgelin
- [KNH Core](../../framework/) with the same version as TPS Tab

TPS Tab is client-side and does not need to be installed on the server.

## Installation and use

Place `knh-core-<version>.jar` and `tps-tab-<version>.jar` in the instance's `mods/` directory. Join a world and hold the normal player-list key (`Tab`) to see the overlay.

The source label distinguishes server-published values from time-sync estimates. Until a source becomes available, the overlay shows a configurable waiting message.

## Configuration

TPS Tab creates `<instance>/config/tab_tps.json`:

```json
{
  "enabled": true,
  "staleDataTicks": 400,
  "showPlaceholder": true,
  "placeholderText": "Waiting for passive TPS data..."
}
```

The file is polled on client ticks, so changes take effect without restarting Minecraft. `staleDataTicks` controls how old a measurement can become before the overlay marks it as stale.

## Build

From the repository root:

```bash
./gradlew -p framework publishToMavenLocal
./gradlew -p mods/tps-tab clean build
```

Artifact:

```text
mods/tps-tab/build/libs/tps-tab-<version>.jar
```
