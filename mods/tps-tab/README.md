# TPS Tab

Hold Tab and see how the server is really doing: TPS and tick time for the whole server, the dimension you're in, and any dimensions you pin.

Client and server.

![tpstab.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/tpstab.png)

## Features

- whole-server TPS and MSPT, plus the current dimension and any dimensions you pin
- numbers come from the server's own tick timers, not client-side guessing, and no Opis is needed
- only asks the server while Tab is held, once a second by default
- TPS and MSPT are coloured by health, and data is marked stale when the server stops answering
- the card sits under the player list, or in the middle of the screen where the game doesn't tell mods where the list is

## Install

Install TPS Tab together with [KNH Core](https://github.com/fopwoc/GTNH-KNH/tree/main/framework) of the same version. Supported loaders and Minecraft versions, and what else to install, are listed in the [main README](https://github.com/fopwoc/GTNH-KNH#install). It goes on **both** the client and the server.

Either side can go without it: a client with TPS Tab can join a server without it and the other way round; the card just stays empty. Both sides need the same TPS Tab version.

## Settings

Client-side, in the loader's config screen, or in `config/tab_tps.cfg` or `config/tab_tps.toml`, depending on the loader:

- **enabled**, **showServerMetrics**, **showCurrentDimensionMetrics**: what the card shows
- **dimensionIds**: dimensions that always stay on the card, comma-separated, as your game names them: numbers like `0, -1, 7`, or ids like `minecraft:overworld, mymod:mining`
- **cardAlignment**: left, center or right, when the card sits under the player list
- **updateIntervalTicks**: how often to ask while Tab is held; 20 is once a second
- **staleDataTicks**: how old an answer can get before it's shown as stale
- **showPlaceholder**, **placeholderText**: what to show before the first answer

## For developers

```bash
./gradlew :tps-tab:buildAll
```

The protocol, monitor and card are shared in `src/commonMain`; tick sampling lives in the per-platform source sets. The server answers from rolling tick-time arrays: whole-server TPS comes from whole-tick MSPT, and a dimension's MSPT is the time spent ticking it. Where the loader doesn't keep per-dimension tick times itself, a KNH Core mixin around the level tick measures them.
