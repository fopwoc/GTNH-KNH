# TPS Tab

TPS Tab adds authoritative server performance information below Minecraft's multiplayer player list. Hold `Tab` to see whole-server TPS/MSPT and the cost of the dimension you are currently in.

![tpstab.png](../../.github/assets/tpstab.png)

## How it works

TPS Tab is one universal jar with sided client and server implementations. It is optional on both sides:

- a client with TPS Tab can join a server without it
- a client without TPS Tab can join a server with it
- measurements activate only when both sides advertise the TPS Tab network channel and use the same protocol

The client sends a small request immediately when the player list opens and then at a client-configured interval while it remains open. The default is every 20 ticks. The server answers each request from the server tick thread. Closing the player list stops requests, and the server sends no unsolicited updates.

The server reads Minecraft's own rolling tick-duration arrays. Whole-server TPS is derived from whole-tick MSPT; dimension MSPT is the time spent ticking that dimension. Dimensions share the server's TPS because Minecraft ticks them on the same server loop. Opis is not required.

## Requirements

- GT New Horizons 2.9.0 beta 2 / Minecraft 1.7.10
- Forgelin
- [KNH Core](../../framework/) with the same build version

Install the same `tps-tab-<version>.jar` and matching `knh-core-<version>.jar` on each side where TPS Tab is wanted. The client uses KNH Core's Compose UI; the dedicated-server path initializes no UI code.

## Configuration

Open **Mods → TPS Tab → Config** to change client settings in game. The same settings are stored in `<instance>/config/tab_tps.cfg` and can still be edited directly while the game is running.

Existing `tab_tps.json` settings are imported once when `tab_tps.cfg` does not yet exist. The legacy JSON file is left untouched.

Set `showAllDimensions` to `true` to request every currently loaded dimension instead of only the player's current dimension. Changes are detected while the game is running; changing the dimension scope sends an immediate refreshed request if `Tab` is open.

`updateIntervalTicks` controls the request frequency while `Tab` is held. The default `20` is once per second, `1` is 20 updates per second, and `200` is once every 10 seconds. The stale-data threshold is kept at least twice the update interval so intentionally slow updates are not immediately marked stale.

## Build

From the repository root:

```bash
./gradlew -p framework publishToMavenLocal
./gradlew -p mods/tps-tab clean build
```

The universal jar is written to `mods/tps-tab/build/libs/`.
