# KNH Core

The library under every KNH mod: real AndroidX Jetpack Compose for screens and HUDs, plus the plumbing a mod needs (configs, networking, key bindings, commands, storage), with one API on every loader KNH supports.

**Compose that builds native Minecraft UI.** The real AndroidX Compose Runtime drives a tree of KNH nodes, which KNH lays out and draws with Minecraft's own GUI rendering and the game's input. Text is the game's font, buttons, checkboxes and sliders use the vanilla widget textures, and scaling and HUD layering are the game's. A KNH screen looks and behaves like the rest of the game.

It does nothing on its own. Players only need it because the mods do.

## Install

Put the KNH Core jar for your loader in `mods/`, next to the mods that need it. Their versions must match; a mismatch is reported at startup. Supported loaders and Minecraft versions, and what else to install, are listed in the [main README](https://github.com/fopwoc/GTNH-KNH#install).

## For developers

### What's inside

The jar bundles the actual AndroidX libraries:

- **Compose Runtime**, with `runtime-saveable`: composition, snapshot state and effects
- **Lifecycle and ViewModel**: lifecycle owners, `ViewModel`, `viewModel()` and `viewModelScope`
- **Navigation 3 Runtime**: `NavKey`, `NavBackStack`, `NavEntry` and `entryProvider`
- **kotlinx.serialization** JSON

The Kotlin stdlib and coroutines come from the loader's Kotlin mod: Forgelin, Fabric Language Kotlin or Kotlin for Forge.

On top of that, KNH's own Minecraft layer does layout, drawing and input, since Compose UI can't draw into a game GUI. You get:

- **Layout:** `Box`, `Column`, `Row`, `Spacer`, `Text`, `LazyColumn` and modifiers for padding, size, background, border, click and hover
- **Vanilla-looking controls:** `Button`, `Checkbox`, `Slider`, `TextField`, `SelectableList`
- **Mod menu pieces:** `Scaffold`, `Section`, `Panel`, `Card`, `Dialog`, `Tabs`, `SegmentedControl`, `ToggleButton`
- **`GpuCanvas`:** draws large prepared RGBA images, like map tiles, from a GPU texture array, and swaps frames without recomposing
- **Theming:** `MinecraftTheme` holds colour and text roles, like `MaterialTheme`
- **Navigation:** a `NavHost` that renders Navigation 3 entries and closes on Escape through `BackHandler`

### Getting started

A mod writes its UI and logic once in common code against KNH Core's API, and each loader only hands a `ModEntrypoint` to `Platform.initialize`. Development jars, with sources, are on a Maven repository at `https://fopwoc.github.io/GTNH-KNH/`. Any Gradle setup can use them: a plain Loom, NeoForge or GTNHGradle project, or [KnhMP](https://github.com/fopwoc/GTNH-KNH/tree/main/knhmp) to build one source tree for several loaders.

The [developer guide](https://github.com/fopwoc/GTNH-KNH/blob/main/framework/GUIDE.md) covers setup, every API and how the renderer works. The `testgui` [storybook](https://github.com/fopwoc/GTNH-KNH/tree/main/mods/testgui) shows every component in its states: run `/testgui`.

## Thanks

Above all, [Jetpack Compose](https://developer.android.com/compose). Its runtime is what makes KNH possible: composition, snapshot state and effects were designed so well that they drive a Minecraft GUI just as happily as an Android app.

KNH Core also stands on the work of the Android and JetBrains teams:

- [AndroidX](https://developer.android.com/jetpack/androidx) by Google: [Compose Runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime), [Lifecycle and ViewModel](https://developer.android.com/jetpack/androidx/releases/lifecycle) and [Navigation 3](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Kotlin](https://kotlinlang.org/) by JetBrains, with its [Compose compiler plugin](https://kotlinlang.org/docs/compose-compiler-migration-guide.html), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) and [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

All of them are open source under the Apache License 2.0.
