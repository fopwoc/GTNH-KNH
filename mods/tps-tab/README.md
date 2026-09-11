# TPS Tab

Shows server TPS and tick time on the Tab player list of GT New Horizons (Minecraft 1.7.10). Hold Tab and see how the server is doing and how much of the tick the dimension you are in costs.

![tpstab.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/tpstab.png)

## What it does

- one card under the player list: whole-server TPS / MSPT plus the current dimension and any dimensions you pin
- numbers come from the server itself (Minecraft's own tick timers), not from client-side guessing — no Opis needed
- only asks while Tab is held, once a second by default; nothing is sent otherwise
- marks data as stale when the server stops answering
- colours TPS and MSPT by health

## Install

One universal jar. Put `tps-tab-<version>.jar` and the matching `knh-core-<version>.jar` in `mods/` on **both** the client and the server. Needs Forgelin (already part of GTNH).

Both sides are optional in the protocol sense: a client with the mod can join a server without it and vice versa — the card just stays empty (or shows the placeholder text) when the other side does not have it.

Versions of TPS Tab and KNH Core must match.

## Settings

**Mods → TPS Tab → Config** or `config/tab_tps.cfg` (client only):

- `enabled`, `showServerMetrics`, `showCurrentDimensionMetrics`
- `dimensionIds` — comma-separated list such as `0, -1, 7` of dimensions that stay on the card wherever you are
- `cardAlignment` — left / center / right under the player list
- `updateIntervalTicks` — request cadence while Tab is held (20 = once a second)
- `staleDataTicks` — age after which the last answer is shown as stale (kept at least twice the update interval)
- `showPlaceholder`, `placeholderText` — what to show before the first server answer

## How it works

The client sends a small request when the player list opens and repeats it on the configured interval while it stays open. The server answers from the tick thread with its rolling tick-time arrays: whole-server TPS is derived from whole-tick MSPT, dimension MSPT is the time spent ticking that dimension. Dimensions share the server's TPS because Minecraft ticks them all on the same loop.

## Build

```bash
./gradlew -p framework publishToMavenLocal
./gradlew -p mods/tps-tab clean build
```

Jar: `mods/tps-tab/build/libs/tps-tab-<version>.jar`. See the [repository README](../../README.md) for the full build.
