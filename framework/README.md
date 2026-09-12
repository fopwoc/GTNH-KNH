# KNH Core

KNH (Kotlin New Horizons) Core is a library mod for GT New Horizons (Minecraft 1.7.10). It is required by [Measure](../mods/measure/), [TPS Tab](../mods/tps-tab/) and [Hotspot](../mods/hotspot/) and does nothing visible on its own.

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
- `WorldOverlay`: glass boxes and spheres (with an inside grid), corner brackets, outlines, lines, depth-ghosted markers and labels drawn in the world from `RenderWorldLastEvent`
- `ComposeGuiScreen` for screens, `ComposeMenuScreen` + `Scaffold`/`Section`/`Dialog` for mod menus, `ComposeHudOverlay` for HUD elements
- `MinecraftTheme`: colour and text roles via composition locals, like `MaterialTheme`
- `ClientKeyBindings`, `ClientCommand`, `ScreenOpener`: key → action, `/command` → reply, open a screen on the next tick
- `ForgeConfig`: declarative Forge `.cfg` settings with a generated in-game config screen
- `JsonFileStorage` for mod state files, `WorldScopedJsonStore` + `WorldScopedSync` for per-world/server state
- `ModChannel` + `VersionedMessage`: client ↔ server messages whose decoding never throws (a throw would kick the player), with bounds-checked reads and channel-availability tracking

Read the [developer guide](GUIDE.md) — setup, layout, controls, lists, state and ViewModels, navigation, HUD, settings, storage, testing, internals. The [`testgui`](../mods/testgui/) module is a storybook of everything: run `/testgui` in a dev instance.

Stable packages are `ui.compose.foundation`, `ui.compose.component`, `ui.compose.component.native`, `ui.compose.model`, `ui.compose.state`, `ui.compose.runtime`, `ui.compose.navigation`, `ui.compose.minecraft`, `ui.compose.theme`, `config`, `network`, `serialization`, `format`, `render` and `client`. `ui.compose.node` and most of `ui.compose.layout` are internals and may change.

### Runtime notes

- The jar bundles Compose Runtime, kotlinx.serialization and the AndroidX lifecycle artifacts. Kotlin stdlib and coroutines come from Forgelin: the stdlib is pinned to the version Forgelin embeds, coroutines are compiled straight from the copy shaded inside the Forgelin jar, and the build refuses to bundle either.
- Mods using KNH Core declare `forgelin` and `knhcore` as dependencies in `mcmod.info` and call `FrameworkMod.checkDependent(modId, version)` from `preInit` to get a clear error on version mismatch.

### Build

```bash
./gradlew -p framework clean build publishToMavenLocal
```

Maven Local coordinates: `io.github.fopwoc.mods:knh-core:<version>`. Jar: `framework/build/libs/knh-core-<version>.jar`.
