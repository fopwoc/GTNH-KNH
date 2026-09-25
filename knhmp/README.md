# KnhMP

Kotlin Multiplatform's odd cousin that only knows Minecraft. You write one mod module with a KMP-style source-set tree; KnhMP compiles it for every loader and Minecraft version you declare, each in its own isolated Gradle build, because GTNHGradle, Loom and ModDevGradle refuse to share one.

It builds everything in this repository: GTNH 1.7.10 through GTNHGradle, and Fabric and NeoForge 26.2 through Loom and ModDevGradle.

```kotlin
knhmp {
    modId = "hello"

    sourceSets {
        commonMain { jvmTarget = 24 }
        gtnhMain { dependsOn(commonMain) }
        val modernMain = sourceSet("modernMain").apply {
            dependsOn(commonMain)
            jvmTarget = 25
        }
        fabricMain { dependsOn(modernMain) }
        neoforgeMain { dependsOn(modernMain) }
    }

    targets {
        gtnh { /* GTNHGradle convention, Forgelin */ }
        fabric { minecraft("26.2") /* Loom, Fabric API */ }
        neoforge { minecraft("26.2") /* ModDevGradle, NeoForge */ }
    }
}
```

Then `./gradlew :hello:buildAll` builds a jar per loader and version into `build/libs/`, while IntelliJ sees the module as one ordinary Kotlin Multiplatform project.

What you get:

- **A source graph**, like KMP's: each jar compiles its leaf and the leaf's parents, never a sibling's code
- **Islands:** generated compiler builds under `.knhmp/`, one per compatible build-tool stack, that the native tools own completely
- **Exact module dependencies:** `implementation(projects.framework)` resolves to the framework's node for the same loader and Minecraft version
- **One IDE model** for editing and navigation across every target
- **Checks** that the right sources, bytecode levels and Mixin metadata actually ended up in each jar
- **Maven publishing** of each target's development jar, with its libraries in the POM, for other developers to build against

## Use it

This repository includes it as a composite build from `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("knhmp")
}
```

Then apply `id("io.github.fopwoc.knhmp")` in a module. The [architecture](ARCHITECTURE.md) documents the whole DSL, how islands form, what each backend does, and the current limitations.

## Build

From the repository root:

```bash
./gradlew -p knhmp build
```
