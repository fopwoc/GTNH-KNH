# TPS Tab

Shows server TPS and tick time while the player list is open on GTNH 1.7.10, Fabric 26.2, and NeoForge 26.2. Hold Tab to see how the server is doing and how much of the tick the dimension you are in costs.

![tpstab.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/tpstab.png)

## What it does

- one card under the player list on GTNH, or at the center of the screen on 26.2: whole-server TPS / MSPT plus the current dimension and any dimensions you pin
- numbers come from the server itself (Minecraft's own tick timers), not from client-side guessing — no Opis needed
- only asks while Tab is held, once a second by default; nothing is sent otherwise
- marks data as stale when the server stops answering
- colours TPS and MSPT by health

## Install

Install the TPS Tab and KNH Core jars for the same loader, Minecraft version, and build version on **both** the client and the server. GTNH needs Forgelin; Fabric needs Fabric API and Fabric Language Kotlin; NeoForge needs Kotlin for Forge.

Both sides are optional in the protocol sense: a client with the mod can join a server without it and vice versa — the card just stays empty (or shows the placeholder text) when the other side does not have it.

Versions of TPS Tab and KNH Core must match.

## Settings

Settings use the loader's native config file and screen where available (client only):

- `enabled`, `showServerMetrics`, `showCurrentDimensionMetrics`
- `dimensionIds` — comma-separated IDs of dimensions that stay on the card: `0, -1, 7` on GTNH or `minecraft:overworld, mymod:mining` on 26.2
- `cardAlignment` — left / center / right under the player list on GTNH; the 26.2 card stays centered
- `updateIntervalTicks` — request cadence while Tab is held (20 = once a second)
- `staleDataTicks` — age after which the last answer is shown as stale (kept at least twice the update interval)
- `showPlaceholder`, `placeholderText` — what to show before the first server answer

## How it works

The client sends a small request when the player list opens and repeats it on the configured interval while it stays open. The server answers from the tick thread with rolling tick-time arrays: whole-server TPS is derived from whole-tick MSPT, dimension MSPT is the time spent ticking that dimension. Fabric's dimension timing is measured by a framework Mixin around `ServerLevel.tick`; NeoForge and GTNH expose their own per-world arrays. Dimensions share the server's TPS because Minecraft ticks them all on the same loop. Protocol version 2 carries string dimension IDs and requires matching TPS Tab versions on both sides.

## Build

```bash
./gradlew :tps-tab:buildAll
```

KNH Core is built first as a module dependency. Loader jars are collected in `mods/tps-tab/build/libs/`. Protocol, monitor state, and the card live in `src/commonMain`; Minecraft 26.2 sampling lives in `src/modernMain`; loader entrypoints and GTNH sampling live in their respective source sets. See the [repository README](../../README.md) for the full build.
