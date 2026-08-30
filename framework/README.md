# KNH Core

KNH Core is the shared runtime library for the GTNH Kotlin mods in this repository. It is distributed as a separate Forge mod and must be installed alongside every player-facing mod in the suite.

> [!WARNING]
> KNH Core is currently an experimental API. Public packages may change before the first stable release.

## Features

- shared proxy and mod infrastructure
- JSON serialization and live configuration helpers
- file-backed configuration utilities
- a declarative Minecraft GUI layer powered by Compose Runtime. Yeah, real Jetpack Compose!
- AndroidX lifecycle and `ViewModel` integration without the Compose Desktop UI runtime
- native Minecraft/Forge rendering, input, widgets, scrolling, clipping, and navigation

The jar bundles the Kotlin libraries required by the framework, including serialization, Compose Runtime, coroutines, and the selected AndroidX lifecycle artifacts. Forgelin remains an external runtime dependency.

## GUI framework

The supported authoring surface is grouped into these packages:

| Package | Purpose |
| --- | --- |
| `ui.compose.foundation` | Layout and text primitives such as `Box`, `Column`, `Row`, `Spacer`, and `Text`. |
| `ui.compose.component.native` | Compose bindings for native buttons, checkboxes, text fields, sliders, and selectable lists. |
| `ui.compose.component` | Higher-level components such as panels, tabs, toggle buttons, and segmented controls. |
| `ui.compose.model` | Modifiers, alignment, styles, colors, and immutable UI models. |
| `ui.compose.state` | Scroll state and related state holders. |
| `ui.compose.runtime` | Composition, lifecycle, saveable-state, and `ViewModel` integration. |
| `ui.compose.navigation` | Stack navigation and `NavHost`. |
| `ui.compose.minecraft` | Minecraft screen hosting and rendering integration. |

Packages such as `ui.compose.node` and most of `ui.compose.layout` are implementation details and are not a stable external API.

### Minimal screen

```kotlin
class ExampleScreen : ComposeGuiScreen() {
    override val composeBackgroundStyle = ComposeBackgroundStyle.VanillaDefault

    @Composable
    override fun Content() {
        Column {
            Text("Hello from Compose Runtime")
            Button(
                text = "Close",
                onClick = { mc.displayGuiScreen(null) }
            )
        }
    }
}
```

`ComposeGuiScreen` owns a Compose runtime and an AndroidX `ViewModelStore`. Recomposition and `initGui()` do not recreate screen-scoped view models; closing the GUI clears them.

Available background policies are `VanillaDefault`, `None`, and `Color(...)`. Packed colors use explicit ARGB semantics, so use `Color(0xFF101010)` for an opaque value.

For a working catalog of controls, layout, state, navigation, and stress cases, see the [`testgui`](../mods/testgui/) module and run `/testgui` in a development instance.

## Runtime requirements

- Minecraft 1.7.10 / GT New Horizons 2.9.0-beta-2
- Forgelin

Mods using KNH Core should declare both `forgelin` and `knhcore` as dependencies in `mcmod.info`.

## Build and publish locally

From the repository root:

```bash
./gradlew -p framework clean build publishToMavenLocal
```

Artifacts:

```text
framework/build/libs/knh-core-<version>.jar
framework/build/libs/knh-core-<version>-sources.jar
```

Local Maven coordinates:

```text
io.github.fopwoc.mods:knh-core:<version>
```
