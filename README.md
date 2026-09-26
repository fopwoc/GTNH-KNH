# KNH — Kotlin New Horizons

[![CI](https://github.com/fopwoc/GTNH-KNH/actions/workflows/ci.yml/badge.svg)](https://github.com/fopwoc/GTNH-KNH/actions/workflows/ci.yml)

Kotlin mods for [GT New Horizons](https://www.gtnewhorizons.com/) and for modern Minecraft on Fabric and NeoForge, written once and built for every loader. Their screens and HUDs run on real AndroidX Jetpack Compose.

> [!NOTE]
> This project contains AI-generated code. See [AI_USAGE.md](AI_USAGE.md) for details.

## Mods

| Mod | Side | GTNH 1.7.10 | Fabric 26.2 | NeoForge 26.2 |
| --- | --- | :-: | :-: | :-: |
| [Measure](mods/measure/) | client | ✓ | ✓ | ✓ |
| [TPS Tab](mods/tps-tab/) | client + server | ✓ | ✓ | ✓ |
| [Palimpsest](mods/palimpsest/) | client | ✓ | ✓ | ✓ |
| [Hotspot](mods/hotspot/) | client + server | ✓ | | |

**[Measure](mods/measure/)** is a measuring tape. It draws lines, boxes and spheres in the world with their sizes, keeps them per world, and exports them to rebuild a design somewhere else.

**[TPS Tab](mods/tps-tab/)** shows real server TPS and tick time, for the whole server and per dimension, while you hold Tab.

**[Palimpsest](mods/palimpsest/)** is a world map that remembers every moment it has seen. Scrub back through a long world like a time-lapse, and share the map between your instances through git.

**[Hotspot](mods/hotspot/)** finds what eats server ticks. It profiles through Opis and draws the heaviest chunks and machines right where they stand. It's GTNH-only on purpose.

**[Test GUI](mods/testgui/)** is the storybook of KNH Core's components, for framework development. It isn't released.

### Archived

**[DejaVu](archive/dejavu/)** tried to back up a server world from the client by saving every chunk the server sends. A client doesn't receive enough for a backup, so it became a "where was that base" archive instead. It predates the current KNH Core, is not built, and probably no longer compiles.

## KNH Core

[KNH Core](framework/) is the library every mod here is built on. It gives a mod:

- screens, menus and HUD overlays written as composable functions, with vanilla-looking controls
- in-world drawing: glass boxes and spheres, outlines, labels, markers that ghost through walls
- settings in the loader's native config format, with its own config screen
- client ↔ server messages that never kick a player on a bad packet
- per-world and per-server storage, key bindings and client commands

A mod's code talks to KNH Core, not to the loader, so most of it is shared across GTNH, Fabric and NeoForge.

### AndroidX in Minecraft

KNH Core runs the actual AndroidX libraries, not look-alikes:

- **Compose Runtime**: composition, snapshot state, effects and saveable state
- **Lifecycle and ViewModel**: real lifecycle owners, `ViewModel`, `viewModel()` and `viewModelScope`
- **Navigation 3**: `NavKey`, `NavBackStack` and `NavEntry` as they are
- **kotlinx.serialization** for saved data

Compose UI, the part that draws, expects to own a window and a Skia canvas, which a game GUI doesn't have. So KNH skips it and brings its own Minecraft layer under the runtime: layout, rendering, input, theming, a `NavHost` for Navigation 3, and a GPU canvas for large images like maps. On GTNH it draws with OpenGL; on 26.2 it goes through the game's own renderer, including Vulkan.

The [core README](framework/) is the starting point for mod developers, and the [guide](framework/GUIDE.md) covers everything in depth.

## KnhMP

[KnhMP](knhmp/) is Kotlin Multiplatform's odd cousin that only knows Minecraft. A mod is one module with a familiar-looking source-set tree: `common` code, then `gtnh` and `modern`, then `fabric` and `neoforge`. Behind it, every loader and Minecraft version gets its own isolated Gradle build, because GTNHGradle, Loom and ModDevGradle refuse to share one. The [architecture](knhmp/ARCHITECTURE.md) has the gory details.

## Install

Download the jars from [Releases](https://github.com/fopwoc/GTNH-KNH/releases). Every mod needs the KNH Core jar for the same loader, Minecraft version and build version; a mismatch is reported at startup.

- **GTNH 1.7.10:** KNH Core plus the mod jars. Forgelin and Hodgepodge are already part of the pack. Needs Java 24–26.
- **Fabric 26.2:** also needs Fabric API, Fabric Language Kotlin and Forge Config API Port. Mod Menu is optional, for the config screens.
- **NeoForge 26.2:** also needs Kotlin for Forge.

TPS Tab and Hotspot also go on the server, with KNH Core. Hotspot needs Opis there too.

## Building

Build everything with JDK 26:

```bash
./build.sh
```

It builds, tests, checks formatting and runs detekt on every module, and collects the jars in `artifacts/`. `./gradlew lint` checks just formatting and code smells, without building; style findings are reported as warnings, smells fail. `./gradlew spotlessApply` formats everything. A single module builds with `./gradlew :measure:buildAll`. Versions come from Git: a release tag as is, anything else with the distance from the last tag and the commit.

```text
.
├── framework/     KNH Core
├── mods/          Measure, TPS Tab, Palimpsest, Hotspot, Test GUI
├── archive/       DejaVu, not built
├── knhmp/         the build plugin
└── gradle/        shared versions
```
