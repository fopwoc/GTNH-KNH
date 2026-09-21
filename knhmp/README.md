# KnhMP Gradle Plugin

KnhMP builds one hierarchical Minecraft mod module through isolated GTNHGradle, Loom, and ModDevGradle compiler projects.

The plugin is currently an initial implementation. Its architecture, invariants, supported backends, and limitations are documented in [ARCHITECTURE.md](ARCHITECTURE.md).

## Composite-build usage

Make the plugin available from a consumer's `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../knhmp")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then apply it in a logical module:

```kotlin
plugins {
    id("io.github.fopwoc.knhmp")
}

knhmp {
    // See ARCHITECTURE.md for the complete source graph and target matrix.
}
```

The relative `includeBuild` path depends on the consumer's location. Existing modules in this repository are intentionally not wired to KnhMP yet.

## Build and publication

Use the repository wrapper from the monorepository root:

```bash
./gradlew -p knhmp build
./gradlew -p knhmp publishAllPublicationsToBuildRepository
```

The project publishes the implementation artifact and Gradle plugin marker for `io.github.fopwoc.knhmp`. Local validation writes them to `knhmp/build/repository`. A future release workflow can attach a remote Maven repository to the same publications.

The version comes from `VERSION` when CI supplies it, otherwise from the repository's Git description, with `0.1.0-SNAPSHOT` only as a source-archive fallback.
