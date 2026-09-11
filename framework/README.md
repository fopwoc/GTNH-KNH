# KNH Core

KNH Core is the shared runtime library for the GTNH Kotlin mods in this repository. It is distributed as a separate Forge mod and must be installed alongside every player-facing mod in the suite.

## Features

- shared proxy and mod infrastructure
- `ForgeConfig`: declarative Forge `.cfg` settings with in-game config screens (`ConfigScreen`, `ConfigGuiFactory`)
- JSON file storage helpers (`JsonFileStorage`) for mod state such as saved measurements
- a declarative Minecraft GUI layer powered by Compose Runtime. Yeah, real Jetpack Compose!
- AndroidX lifecycle and `ViewModel` integration without the Compose Desktop UI runtime
- Minecraft/Forge rendering, input, vanilla-styled widgets, scrolling, clipping, and navigation

The jar bundles the Kotlin libraries required by the framework, including serialization, Compose Runtime, coroutines, and the selected AndroidX lifecycle artifacts. Forgelin remains an external runtime dependency.

## GUI framework

The full developer guide — setup, layout, controls, lists, state and ViewModels, navigation, HUD overlays, settings, JSON storage, testing, internals — is in [GUIDE.md](GUIDE.md).

The supported authoring surface is grouped into these packages:

| Package | Purpose |
| --- | --- |
| `ui.compose.foundation` | Layout and text primitives such as `Box`, `Column`, `Row`, `Spacer`, `Text`, and `LazyColumn` (only the visible window is composed; item heights measured or fixed). |
| `ui.compose.component.native` | Vanilla-looking `Button`, `Checkbox`, `Slider` (drawn from the widgets sheet, no vanilla widget instances), `TextField` (selection, clipboard, click-to-place cursor) and `SelectableList`/`MultiSelectableList`. |
| `ui.compose.component` | Higher-level components such as panels, tabs, toggle buttons, and segmented controls. |
| `ui.compose.model` | Modifiers, alignment, styles, colors, and immutable UI models. |
| `ui.compose.state` | `ScrollState`, `LazyListState`, `TextFieldState` and related state holders. |
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

- Minecraft 1.7.10 / GT New Horizons 2.9.0-beta-3
- Forgelin (provides the Kotlin standard library and coroutines at runtime; KNH Core is compiled against exactly the versions Forgelin embeds and does not bundle them)
- Hodgepodge (declared as a hard dependency; the framework is only tested with GTNH's Hodgepodge fixes in place)

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
