# GTNH Kotlin Mods

[![Build](https://github.com/fopwoc/GTNH-KNH/actions/workflows/build.yml/badge.svg)](https://github.com/fopwoc/GTNH-KNH/actions/workflows/build.yml)

A collection of client-side mods and shared Kotlin infrastructure for [GT New Horizons](https://www.gtnewhorizons.com/) on Minecraft 1.7.10.

> [!IMPORTANT]
> This project is under active development. Expect breaking changes and test builds before the first stable release.

> [!NOTE]
> This project contains AI-generated code. See [AI_USAGE.md](AI_USAGE.md) for details.

## Projects

| Project | Type | Description |
| --- | --- | --- |
| [KNH Core](framework/) | Required library | Shared Kotlin runtime, configuration helpers, serialization, and a Compose Runtime-based Minecraft GUI framework. |
| [DejaVu](mods/dejavu/) | Player mod | Archives chunks received by the client into a local, singleplayer-compatible world. |
| [Measure](mods/measure/) | Player mod | Creates persistent line and area measurements with in-world overlays. |
| [TPS Tab](mods/tps-tab/) | Failed experiment | Attempted to expose CoFH/Opis profiling data client-side; only an inaccurate passive estimate remains. |
| [Test GUI](mods/testgui/) | Developer tool | Interactive showcase and stress-test app for the KNH Core GUI framework. |

Each directory is a standalone Gradle build. The root project is a composite build used to keep their shared dependency versions and build conventions together.

## Compatibility

- GT New Horizons 2.9.0-beta-2
- Minecraft 1.7.10
- Forge 10.13.4.1614
- [Forgelin](https://github.com/GTNewHorizons/Forgelin) 2.0.3-GTNH
- the matching KNH Core version for every mod in this repository

The mods are client-side. They do not need to be installed on the server.

## Installation

1. Obtain `knh-core-<version>.jar` and the jar for each mod you want to use.
2. Confirm that Forgelin is present in the GTNH instance.
3. Copy the jars into the instance's `mods/` directory.
4. Start the game and confirm that the mods appear in the Forge mod list.

Do not install `testgui` unless you are developing or testing KNH Core.

The KNH Core and mod versions must match. KNH Core is not embedded into the individual mod jars.

## Building

### Requirements

- Git
- JDK 25
- A Unix-like shell for the all-project build script

The build runs on JDK 25 but emits Java 8-compatible bytecode for Minecraft 1.7.10.

### Build all runtime jars

From the repository root:

```bash
./build.sh
```

The script publishes KNH Core to Maven Local, builds all four mods, and copies the distributable jars to `artifacts/`. Sources and development jars are excluded.

Versions come from the repository state through GTNHGradle. A build on a release tag uses that tag exactly; development builds include the current branch, distance from the latest tag, commit hash, and dirty state. The root script resolves this identity once through GTNHGradle and supplies it to every standalone build.

On macOS, the script locates JDK 25 with `/usr/libexec/java_home`. On other systems, set `JAVA25_HOME` explicitly:

```bash
JAVA25_HOME=/path/to/jdk-25 ./build.sh
```

Set `BUILD_JOBS` to limit parallel module builds:

```bash
BUILD_JOBS=2 ./build.sh
```

### Continuous integration

The `Build jars` GitHub Actions workflow runs the same `build.sh` entry point for changes to `main`, pull requests targeting `main`, semantic version tags, and manual dispatches. Documentation-only changes skip the build. Every successful run publishes one temporary workflow artifact containing all runtime jars from `artifacts/`.

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
│   ├── measure/          measurement toolkit
│   ├── testgui/          framework showcase
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
