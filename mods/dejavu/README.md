# DejaVu

Client-side Kotlin Forge mod for GT New Horizons 2.8 / Minecraft 1.7.10 that archives chunks the client has already received from a world into a local singleplayer-compatible save.

Project layout follows the standard GTNH single-mod structure, with sources in `src/main/kotlin` and resources in `src/main/resources`.

## What it does

- Runs on the client only.
- Periodically scans chunks currently loaded around the player.
- Writes observed chunk data into a local Anvil save under the normal `saves/` directory.
- Preserves block IDs, metadata, chunk sections, biome data, and serializable tile entity state that the server already sent to the client.
- Writes a `level.dat` and a `client-world-backup-manifest.json` sidecar so the exported save can be opened locally and inspected later.
- Provides an in-game status screen instead of an always-on HUD overlay.
- Can render in-world highlights for chunks that have already been written to the local backup.

Backups are stored in paths like:

```text
<instance>/saves/observed-<server-name>-<server-address>/
```

Configuration is stored in:

```text
<instance>/config/dejavu.json
```

Most JSON config edits now hot-reload while the client is running. The main exception is `saveNamePrefix`, which only affects the next backup session root that gets created.

## Important limitations

This mod **cannot** produce a true server backup, because a client only knows what the server has actually sent to it.

That means:

- unseen chunks are missing
- anti-xray or fake client block data will be copied as seen by the client
- server-only data may be incomplete
- some modded tile entities/entities may not serialize perfectly
- the result is best treated as an observed-world archive / recovery save, not an authoritative server backup

## Default behavior

- archives every 15 seconds
- uses current render distance when `maxChunkRadius = 0`
- skips integrated singleplayer worlds by default
- writes the local backup save in creative mode with commands enabled for easier inspection

## Config fields

```json
{
  "enabled": true,
  "autosaveIntervalSeconds": 15,
  "flushEverySavedChunks": 8,
  "maxChunkRadius": 0,
  "saveSingleplayer": false,
  "showHud": false,
  "saveNamePrefix": "observed-",
  "showChunkHighlights": true,
  "highlightOnlyTargetedChunk": false,
  "highlightRenderRadiusChunks": 12,
  "highlightFillAlpha": 0.08,
  "highlightOutlineAlpha": 0.65
}
```

### Chunk highlight behavior

- chunks backed up in earlier sessions render as translucent blue columns
- chunks saved during the current session render as translucent green columns
- the chunk you are currently looking at keeps the same color family but renders with a stronger outline/fill
- `highlightOnlyTargetedChunk` limits rendering to the chunk you are currently looking at (or standing in if nothing is targeted)
- `highlightRenderRadiusChunks` limits how far from the player the overlay is drawn
- run `/backupgui` in chat to open the backup control screen
- `/backupstatus`, `/observedbackup`, and `/obbackup` remain as aliases
- use the GUI buttons to force a capture pass and toggle chunk highlights without reopening the config

## Requirements

- GT New Horizons 2.8 / Minecraft 1.7.10
- `Forgelin` present in the modpack
- `KNH Core` (`knh-core`) present in the modpack
- Java 25 for the Gradle build runtime

## Usage

1. Build the mod and place the jar in your GTNH instance `mods/` folder.
2. Join a server and move around normally.
3. Run `/backupgui` in chat whenever you want to inspect progress, see stats, and use the backup control buttons.
4. After disconnecting, open the generated world from the standard singleplayer world list.

## Loading exported worlds safely

Exported worlds are written so first load happens in the overworld and the save is marked initialized. This avoids trying to spawn directly into a partial modded dimension snapshot on first open.

If you created a backup with an older build of this mod, rejoin the server once with the updated mod so it can rewrite that backup's `level.dat`, or create a fresh backup with the fixed build.

## Build this module locally

```bash
../../gradlew -p . build
```

Expected artifact:

```text
build/libs/
```

## Build in Docker

```bash
../../build.sh
```

