# KNH Core

KNH (Kotlin New Horizons) Core is a library mod for GT New Horizons (Minecraft 1.7.10). It is required by [Measure](../mods/measure/) and [TPS Tab](../mods/tps-tab/) and does nothing visible on its own.

Under the hood it is Jetpack Compose running inside Minecraft 1.7.10: not a look-alike, the real `androidx.compose.runtime` with a custom node tree, layout, vanilla-style rendering and input on top of it. Mod GUIs and HUD overlays are written as ordinary composable functions, the same way you would write an Android screen.

```kotlin
class ExampleScreen : ComposeGuiScreen() {
  @Composable
  override fun Content() {
    var count by remember { mutableStateOf(0) }
    Column {
      Text("Clicked $count times")
      Button(text = "Click", onClick = { count++ })
      Button(text = "Close", onClick = { mc.displayGuiScreen(null) })
    }
  }
}
```

## Install

Put `knh-core-<version>.jar` in `mods/`. Needs Forgelin and Hodgepodge, both part of GTNH. Every mod built on it must use the same KNH Core version; a mismatch is reported at startup.

## For players

That is all. The rest of this page is for mod developers.

## For developers

What you get:

- `Box`, `Column`, `Row`, `Spacer`, `Text`, `LazyColumn` with modifiers (padding, size, background, border, clickable, hover…)
- vanilla-looking `Button`, `Checkbox`, `Slider`, `TextField` (selection, clipboard, cursor placement), `SelectableList` / `MultiSelectableList`
- panels, tabs, toggle buttons, segmented controls
- `remember`, snapshot state, `ViewModel` with AndroidX lifecycle, saveable state, `BackHandler`, stack navigation with `NavHost`
- `ComposeGuiScreen` for screens and `ComposeHudOverlay` for HUD elements
- `ForgeConfig`: declarative Forge `.cfg` settings with a generated in-game config screen
- `JsonFileStorage` for mod state files

Read the [developer guide](GUIDE.md) — setup, layout, controls, lists, state and ViewModels, navigation, HUD, settings, storage, testing, internals. The [`testgui`](../mods/testgui/) module is a live catalog of everything: run `/testgui` in a dev instance.

Stable packages are `ui.compose.foundation`, `ui.compose.component`, `ui.compose.component.native`, `ui.compose.model`, `ui.compose.state`, `ui.compose.runtime`, `ui.compose.navigation`, `ui.compose.minecraft` and `config`. `ui.compose.node` and most of `ui.compose.layout` are internals and may change.

### Runtime notes

- The jar bundles Compose Runtime, kotlinx.serialization and the AndroidX lifecycle artifacts. Kotlin stdlib and coroutines come from Forgelin; KNH Core is compiled against exactly the versions Forgelin ships and refuses to bundle its own.
- Mods using KNH Core declare `forgelin` and `knhcore` as dependencies in `mcmod.info` and call `FrameworkMod.checkDependent(modId, version)` from `preInit` to get a clear error on version mismatch.

### Build

```bash
./gradlew -p framework clean build publishToMavenLocal
```

Maven Local coordinates: `io.github.fopwoc.mods:knh-core:<version>`. Jar: `framework/build/libs/knh-core-<version>.jar`.
