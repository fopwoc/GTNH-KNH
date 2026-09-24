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
| [TPS Tab](mods/tps-tab/) | client + server | Real server and dimension TPS / MSPT on GTNH, Fabric 26.2, and NeoForge 26.2. |
| [Hotspot](mods/hotspot/) | client + server | Server lag, located: profiles through Opis, lists the heaviest chunks and tile entities, highlights the picked ones in the world. |
| [Palimpsest](mods/palimpsest/) | client | Experimental time-layered tile storage and historical read benchmark. Open with `/palimpsest`; currently uses generated data only. |

[Test GUI](mods/testgui/) (a storybook of every core component) is also in the repo, built but not released.

Want to write a mod with Compose? Start with the [developer guide](framework/GUIDE.md).

## Install

1. Get `knh-core-gtnh-<version>.jar` plus the jar of each mod you want from [Releases](https://github.com/fopwoc/GTNH-KNH/releases).
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

The script runs the root Gradle build (`check buildAll`), which builds every module through [KnhMP](knhmp/README.md) compiler islands, and copies the distributable jars to `artifacts/`. Sources and development jars are excluded.

Versions come from the repository state through KnhMP. A build on a release tag uses that tag exactly; development builds include the distance from the latest tag, commit hash, and dirty state. CI can override it with the `VERSION` environment variable.

On macOS, the script locates JDK 26 with `/usr/libexec/java_home`. On other systems, set `JAVA26_HOME` explicitly:

```bash
JAVA26_HOME=/path/to/jdk-26 ./build.sh
```

### Continuous integration

The `Build jars` GitHub Actions workflow checks Kotlin formatting and Detekt, builds and tests KnhMP, validates its local Maven publication, then runs the same `build.sh` entry point for changes to `main`, pull requests targeting `main`, semantic version tags, and manual dispatches. Documentation-only changes skip the build. Every successful run uploads one temporary workflow artifact containing all runtime jars from `artifacts/`. Pull requests, `main`, and manual runs do not publish releases.

Run the Kotlin checks locally with `bash ./lint.sh`. Spotless uses ktfmt's four-space Kotlin style; for the framework, apply it inside its generated GTNH island with `./gradlew -p framework/.knhmp/gtnh spotlessApply` after one root build. Detekt checks the source directly without baselines.

Workflow artifacts are temporary. To publish jars without an expiration date, push a semantic version tag without a prefix:

```bash
git tag 0.1.0
git push origin 0.1.0
```

The workflow verifies that the tagged commit is reachable from `main`, uses the exact tag in every jar and embedded mod manifest, and creates a GitHub Release containing all runtime jars. Its release notes list every commit since the previous reachable version tag and link to the full diff. Release assets remain available until the release or asset is deleted. Separate tag jobs use a Modrinth publishing action for the mods and, when Portal credentials are present, validate and publish KnhMP to the Gradle Plugin Portal. They upload only after their credentials are configured.

For Modrinth, add the `MODRINTH_TOKEN` repository secret with `VERSION_CREATE` permission. Tag CI uses that token to resolve the existing `knh-core`, `knh-measure`, `knh-tps-tab`, and `knh-hotspot` project slugs to their IDs; no project ID variables are needed. The action creates a separate version for each loader and Minecraft version, uploads KNH Core first, and links each mod version to the matching Core version. Hotspot also declares Opis as a required external dependency. Palimpsest remains in the GitHub Release but has no Modrinth project yet. Avoid rerunning a successful Modrinth upload for the same tag; the action creates versions rather than updating existing ones.

For KnhMP, add the `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` repository secrets from the [Gradle Plugin Portal](https://plugins.gradle.org/). Both are required before CI uploads; the first published version may need Portal review. The plugin remains available through the composite build without these credentials.

### Build one mod

Build one module from the root; modules it depends on are built first:

```bash
./gradlew :framework:buildAll
```

The resulting jar is written to that module's `build/libs/` directory.

## Repository layout

```text
.
├── framework/            KNH Core shared runtime
├── mods/
│   ├── hotspot/          in-world lag profiler on top of Opis
│   ├── measure/          measurement toolkit
│   ├── palimpsest/       layered tile storage prototype
│   ├── testgui/          framework storybook
│   └── tps-tab/          tab-list TPS overlay
├── archive/              retired experiments, not built (DejaVu)
├── knhmp/                multi-loader build plugin
├── gradle/               shared versions and wrapper
├── build.sh              build and artifact collection
└── settings.gradle.kts   root build: KnhMP plugin and modules
```

## Development notes

- Shared dependency versions live in `gradle/libs.versions.toml`.
- Each module declares its source graph and targets in a `knhmp { }` block; loader-independent code lives in `src/commonMain`, GTNH code in `src/gtnhMain`.
- Mods depend on KNH Core as a KnhMP module dependency; no Maven Local publishing is involved.
- Runtime configuration and saved data are kept inside the Minecraft instance, not the repository. See each mod's README for paths and controls.
