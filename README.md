# GTNH Kotlin monorepo

This workspace now groups four standalone Gradle builds in a path layout that mirrors `framework` plus `mods/*`:

- `framework/` — shared framework published to `mavenLocal`
- `mods/dejavu/` — DejaVu backup mod
- `mods/measure/` — measure tool mod
- `mods/tps-tab/` — Tps tab overlay mod

## Shared framework

The shared framework provides reusable building blocks for the mods:

- a common `ModProxy` base class
- a shared `kotlinx.serialization` dependency surface
- reusable JSON/config file helpers for consistent config persistence

The framework is now built as a separate runtime mod jar. The gameplay mods do not shade the framework into their own artifacts anymore, so `DejaVu`, `measure`, and `Tps tab` should be distributed together with `KNH Core` (`knh-core`) the same way they already require `Forgelin`.

It is published locally as:

- group: `io.github.fopwoc.mods`
- artifact: `knh-core`
- version: `0.1.0`

## Build everything

Use the root build script to publish the framework first, then build every mod, then collect a flat list of the runtime jars needed for a modpack directly into `artifacts/`:

```bash
./build.sh
```

The script uses Java 25 for both the shared framework and all GTNH mod builds.

The resulting `artifacts/` folder is intended to be copied into a GTNH instance `mods/` directory and now contains the runtime jars only:

- `knh-core-<version>.jar`
- `dejavu-<version>.jar`
- `measure-<version>.jar`
- `tps-tab-<version>.jar`

## Git migration note

The workspace is now intended to be used as a root-level monorepo. The old nested repositories were archived into `.git-archives/` so the root repository can track the real file contents instead of embedded repositories.

