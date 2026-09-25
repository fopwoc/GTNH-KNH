# TPS Tab

Hold Tab and see how the server is really doing: TPS and tick time for the whole server, the dimension you're in, and any dimensions you pin.

Client and server: GT New Horizons 1.7.10, Fabric 26.2 and NeoForge 26.2.

![tpstab.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/tpstab.png)

## Features

- whole-server TPS and MSPT, plus the current dimension and any dimensions you pin
- numbers come from the server's own tick timers, not client-side guessing, and no Opis is needed
- only asks the server while Tab is held, once a second by default
- TPS and MSPT are coloured by health, and data is marked stale when the server stops answering
- on GTNH the card sits under the player list; on 26.2 it's centred on the screen

## Install

Install TPS Tab and [KNH Core](../../framework/) for the same loader and version, on **both** the client and the server.

- **GTNH:** nothing else, Forgelin is part of the pack
- **Fabric:** Fabric API, Fabric Language Kotlin, Forge Config API Port
- **NeoForge:** Kotlin for Forge

Either side can go without it: a client with TPS Tab can join a server without it and the other way round; the card just stays empty. Both sides need the same TPS Tab version.

## Settings

Client-side, in the loader's config screen, or in `config/tab_tps.cfg` (GTNH) or `config/tab_tps.toml` (Fabric, NeoForge):

- **enabled**, **showServerMetrics**, **showCurrentDimensionMetrics**: what the card shows
- **dimensionIds**: dimensions that always stay on the card, comma-separated: `0, -1, 7` on GTNH, or `minecraft:overworld, mymod:mining` on 26.2
- **cardAlignment**: left, center or right under the player list (GTNH only)
- **updateIntervalTicks**: how often to ask while Tab is held; 20 is once a second
- **staleDataTicks**: how old an answer can get before it's shown as stale
- **showPlaceholder**, **placeholderText**: what to show before the first answer

## For developers

```bash
./gradlew :tps-tab:buildAll
```

The protocol, monitor and card are shared in `src/commonMain`; tick sampling for 26.2 lives in `src/modernMain`, and for 1.7.10 in `src/gtnhMain`. The server answers from rolling tick-time arrays: whole-server TPS comes from whole-tick MSPT, and a dimension's MSPT is the time spent ticking it. On Fabric, per-dimension timing comes from a KNH Core mixin around `ServerLevel.tick`; NeoForge and GTNH keep their own per-dimension arrays.
