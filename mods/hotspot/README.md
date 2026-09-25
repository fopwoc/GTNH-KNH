# Hotspot

Finds what eats server ticks and shows it where it stands. Built for GT New Horizons, where lag is almost always tile entities: machines, pipes, cables.

Client and server: GT New Horizons 1.7.10 only.

Opis already measures every tile entity on the server; Hotspot is the in-game front end for it. Profile for a few seconds, pick the heaviest chunks and the machines inside them, and they get drawn right in the world: a tinted column per chunk, a box per machine, with the milliseconds on top. No external window, no teleporting, no op.

![hotspot1.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/hotspot1.png)
![hotspot2.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/hotspot2.png)

## Features

- one button profiles the server for 1–60 seconds
- chunks per dimension, heaviest first, with block and entity counts
- click a chunk to highlight it in the world and list its tile entities, heaviest first
- select tile entities (Ctrl/Cmd + click, Shift + click, Ctrl/Cmd + A) to box them in the world with their cost, name and class
- colours go from green to red relative to the heaviest highlighted item
- entities are folded into chunk totals; block ticks and other per-world work show as "other"
- the last snapshot and your picks are saved per world or server and come back next time; picks survive a re-profile while the same blocks are still listed
- works from Freecam: the overlay follows the camera, not the player

## Install

Install Hotspot and [KNH Core](../../framework/) on **both** the client and the server. The server also needs **Opis**, which is part of GTNH, as is Forgelin.

## Who may profile

Not ops: the server decides, in `config/hotspot-server.cfg`:

```
allowedPlayers = aspirin, friend        # names or UUIDs
allowEveryone = false
maxDurationSeconds = 15
minMicrosPerTileEntity = 5              # cheaper tile entities are counted, not listed
maxListedTileEntitiesPerChunk = 128
```

The file is re-read when it changes, no restart needed. The singleplayer host is always allowed. If the server lacks Hotspot or Opis, or you're not on the list, the menu shows a short explanation instead.

## Use

Open the menu with `/hotspot`, or bind **Open Hotspot menu** under Controls (unbound by default).

1. Pick a window and press **Profile**. A countdown shows above the hotbar; you can close the menu meanwhile.
2. The menu opens on the dimension you're in (`<` `>` switch dimensions), with its tick time split into blocks, entities and other, and the chunk list.
3. Click a chunk: it gets a glass column in the world, and its tile entities are listed on the right.
4. Select tile entities: they get glass boxes labelled like `2.31 ms`, with the machine name and class.
5. **Deselect** drops every highlight and keeps the snapshot; **Profile** again replaces it.

`/hotspot profile [seconds]` and `/hotspot deselect` do the same without the menu.

## Settings

In **Mods → Hotspot → Config**, or `config/hotspot.cfg` on the client: the default window, labels and class names on or off, label distance and chunk column height.

## For developers

```bash
./gradlew :hotspot:buildAll
```

The server switches on MobiusCore's profiler, the same hooks Opis uses, for the requested number of ticks. It reads per-tile-entity and per-entity timings straight from the profiler, names them (the GregTech machine name, else the block's item name, else the class), groups them by chunk, and streams the result to the client in pages under the packet size limit. It never goes through Opis' own commands or permissions, and several players asking at once share one run.
