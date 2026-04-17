# Measure

Client-side GTNH measurement toolkit for Minecraft 1.7.10.

Project layout follows the shared monorepo conventions used by `framework` and the other `mods/*` builds, with Kotlin sources in `src/main/kotlin` and resources in `src/main/resources`.

## What it does

- provides line and area measurement tools
- renders measurement overlays in-world
- stores measurement data through the shared framework runtime

## Requirements

- GT New Horizons 2.8 / Minecraft 1.7.10
- `Forgelin` present in the modpack
- `KNH Core` (`knh-core`) present in the modpack
- Java 25 for the Gradle build runtime

## Build this module locally

```bash
../../gradlew -p . build
```

## Expected artifact

```text
build/libs/measure-<version>.jar
```

## Build with the root orchestrator

```bash
../../build.sh
```

