# KNH — Kotlin New Horizons

[![Build](https://github.com/fopwoc/GTNH-KNH/actions/workflows/build.yml/badge.svg)](https://github.com/fopwoc/GTNH-KNH/actions/workflows/build.yml)

**Real Jetpack Compose inside [GT New Horizons](https://www.gtnewhorizons.com/).** KNH runs `androidx.compose.runtime` for composition, state and effects; AndroidX Lifecycle and ViewModel for screen state; and `androidx.navigation3.runtime` for typed navigation. Its own Minecraft layer handles layout, rendering and input for mod GUIs and HUDs.

**Run with Java 24–26.** Other Java versions are unsupported.

> [!NOTE]
> This project contains AI-generated code. See [AI_USAGE.md](AI_USAGE.md) for details.

## Mods

| Mod | Side | What it is |
| --- | --- | --- |
| [KNH Core](framework/) | library | Shared by the mods. Real AndroidX Compose Runtime, Lifecycle, ViewModel, and Navigation 3, plus in-world drawing, config, storage and networking helpers. |
| [Measure](mods/measure/) | client | Measuring tape: lines, boxes, spheres drawn in the world, saved per world, exportable, freecam-aware. |
| [TPS Tab](mods/tps-tab/) | client + server | Real server TPS / MSPT on the Tab player list. |
| [Hotspot](mods/hotspot/) | client + server | Server lag, located: profiles through Opis, lists the heaviest chunks and tile entities, highlights the picked ones in the world. |
| [Palimpsest](mods/palimpsest/) | client | Experimental time-layered tile storage and historical read benchmark. Open with `/palimpsest`; currently uses generated data only. |
| [DejaVu](mods/dejavu/) | client, not released | Failed experiment: back up a server world from the client by archiving the chunks you receive into a local singleplayer world. The client just does not get enough — it copies terrain, but loses most block state and every tile entity inventory. Kept building, not developed further. |

[Test GUI](mods/testgui/) (a storybook of every core component) is also in the repo, built but not released.

Want to write a mod with Compose? Start with the [developer guide](framework/GUIDE.md).

## Install

1. Get `knh-core-<version>.jar` plus the jar of each mod you want from [Releases](https://github.com/fopwoc/GTNH-KNH/releases).
2. Drop them into the instance's `mods/` folder.
3. For TPS Tab and Hotspot, also put their jars and KNH Core on the server (Hotspot needs Opis there too).

KNH Core and mod versions must match; a mismatch is reported at startup.

Tested with GT New Horizons 2.9.0-beta-3 (Forge 10.13.4.1614, Forgelin 2.0.3-GTNH, Hodgepodge). Forgelin and Hodgepodge are part of the pack.

## Building

### Requirements

- Git
- JDK 26
- A Unix-like shell for the all-project build script

### Build all runtime jars

From the repository root:

```bash
./build.sh
```

The script publishes KNH Core to Maven Local, builds everything, and copies the distributable jars to `artifacts/`. Sources and development jars are excluded.

Versions come from the repository state through GTNHGradle. A build on a release tag uses that tag exactly; development builds include the current branch, distance from the latest tag, commit hash, and dirty state. The root script resolves this identity once through GTNHGradle and supplies it to every standalone build.

On macOS, the script locates JDK 26 with `/usr/libexec/java_home`. On other systems, set `JAVA26_HOME` explicitly:

```bash
JAVA26_HOME=/path/to/jdk-26 ./build.sh
```

Set `BUILD_JOBS` to limit parallel module builds:

```bash
BUILD_JOBS=2 ./build.sh
```

### Continuous integration

The `Build jars` GitHub Actions workflow checks Kotlin formatting and Detekt across all seven builds, then runs the same `build.sh` entry point for changes to `main`, pull requests targeting `main`, semantic version tags, and manual dispatches. Documentation-only changes skip the build. Every successful run publishes one temporary workflow artifact containing all runtime jars from `artifacts/`.

Run the Kotlin checks locally with `bash ./lint.sh`. Spotless uses ktfmt's four-space Kotlin style; for one module, apply it with `./gradlew -p framework spotlessKotlinApply spotlessKotlinGradleApply` (replace `framework` with the module path). Detekt checks the source directly without baselines.

Workflow artifacts are temporary. To publish jars without an expiration date, push a semantic version tag without a prefix:

```bash
git tag 0.1.0
git push origin 0.1.0
```

The workflow verifies that the tagged commit is reachable from `main`, uses the exact tag in every jar and embedded mod manifest, and creates a GitHub Release containing all runtime jars. Its release notes list every commit since the previous reachable version tag and link to the full diff. Release assets remain available until the release or asset is deleted.

### Build one mod

Publish the framework first, then build the selected module:

```bash
./gradlew -p framework clean publishToMavenLocal
./gradlew -p mods/measure clean build
```

The resulting jar is written to that module's `build/libs/` directory.

## Repository layout

```text
.
├── framework/            KNH Core shared runtime
├── mods/
│   ├── dejavu/           observed-world archiver
│   ├── hotspot/          in-world lag profiler on top of Opis
│   ├── measure/          measurement toolkit
│   ├── palimpsest/       layered tile storage prototype
│   ├── testgui/          framework storybook
│   └── tps-tab/          tab-list TPS overlay
├── gradle/               shared versions and conventions
├── build.sh              build and artifact collection
└── settings.gradle.kts   Gradle composite-build declaration
```

## Development notes

- Shared dependency versions live in `gradle/libs.versions.toml`.
- Common build behavior lives in `gradle/gtnh-module-conventions.gradle.kts`.
- Mods compile against KNH Core from Maven Local; run `publishToMavenLocal` after changing its public API.
- Runtime configuration and saved data are kept inside the Minecraft instance, not the repository. See each mod's README for paths and controls.
