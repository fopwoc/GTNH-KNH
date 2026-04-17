# KNH Core

Shared runtime library mod for the mods in this monorepo.

## What it provides

- reusable shared Kotlin code used by the other mods
- shared JSON/config helpers
- bundled `kotlinx.serialization` runtime classes
- a separate Forge/FML mod jar that other mods can depend on at runtime, similar to `Forgelin`

## Runtime role

This jar is intended to be shipped in the modpack alongside:

- `Forgelin`
- `DejaVu`
- `measure`
- `Tps tab`

The other mods compile against this framework but do not shade it into their own jars.

## Build locally

```bash
../gradlew -p . build publishToMavenLocal
```

## Expected artifact

```text
build/libs/knh-core-<version>.jar
```

