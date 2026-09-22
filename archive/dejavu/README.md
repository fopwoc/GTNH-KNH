# DejaVu

A failed experiment, kept in the repo because it still compiles and is occasionally useful.

The idea was to back up a server world from the client: while you play, DejaVu writes every chunk the server sends you into a local singleplayer-compatible save. It turns out a client just does not receive enough to make that a backup. What you get is terrain and a picture of the world — with most block state degraded and every tile entity inventory empty.

It is not released and not developed further. Use it as a "where was that base again" archive, never as a backup.

![dejavu1.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/dejavu1.png)
![dejavu2.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/dejavu2.png)

## What actually survives

- block IDs, metadata and biomes of every chunk that was loaded around you
- whatever tile entity NBT the server chose to send (mostly rendering data, no inventories)
- a `level.dat` so the save shows up in the singleplayer list, opened in creative with commands enabled

## What does not

- anything you never loaded
- entities, contents of chests / machines / pipes, server-only state
- anti-xray fakes are archived exactly as the client saw them
- machines and multiblocks from GTNH mods usually come back as inert blocks

## Use

1. Put `dejavu-<version>.jar` and the matching `knh-core-<version>.jar` in `mods/` (build them yourself, there are no releases).
2. Play on a server. Chunks are captured on a timer while loaded.
3. `/backupgui` (aliases `/backupstatus`, `/observedbackup`, `/obbackup`) shows progress, forces a capture pass, toggles chunk highlights. Archived chunks are outlined blue, ones captured this session green.
4. Disconnect, then open `saves/observed-<server-name>-<server-address>/` from the singleplayer menu. Copy it first if you care about it.

## Settings

**Mods → DejaVu → Config** or `config/dejavu.cfg`: enable/disable, autosave interval, flush size, chunk radius (0 = render distance), also archive singleplayer, status HUD, save name prefix, chunk highlight options.

## Build

```bash
./gradlew -p framework publishToMavenLocal
./gradlew -p mods/dejavu clean build
```

Jar: `mods/dejavu/build/libs/dejavu-<version>.jar`.
