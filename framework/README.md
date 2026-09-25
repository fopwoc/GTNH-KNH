# KNH Core

The library under every KNH mod: real AndroidX Jetpack Compose for screens and HUDs, plus the plumbing a mod needs (configs, networking, key bindings, commands, storage), with one API on GT New Horizons 1.7.10, Fabric 26.2 and NeoForge 26.2.

It does nothing on its own. Players only need it because the mods do.

## Install

Put the KNH Core jar for your loader and version in `mods/`, next to the mods that need it. Their versions must match; a mismatch is reported at startup.

- **GTNH:** Forgelin and Hodgepodge, both part of the pack. Needs Java 24 or newer.
- **Fabric:** Fabric API, Fabric Language Kotlin, Forge Config API Port. Mod Menu is optional, for config screens.
- **NeoForge:** Kotlin for Forge.

## For developers

### What's inside

The jar bundles the actual AndroidX libraries:

- **Compose Runtime**, with `runtime-saveable`: composition, snapshot state and effects
- **Lifecycle and ViewModel**: lifecycle owners, `ViewModel`, `viewModel()` and `viewModelScope`
- **Navigation 3 Runtime**: `NavKey`, `NavBackStack`, `NavEntry` and `entryProvider`
- **kotlinx.serialization** JSON

The Kotlin stdlib and coroutines come from the loader's Kotlin mod: Forgelin on GTNH, Fabric Language Kotlin and Kotlin for Forge on 26.2.

On top of that, KNH's own Minecraft layer does layout, drawing and input, since Compose UI can't draw into a game GUI. You get:

- **Layout:** `Box`, `Column`, `Row`, `Spacer`, `Text`, `LazyColumn` and modifiers for padding, size, background, border, click and hover
- **Vanilla-looking controls:** `Button`, `Checkbox`, `Slider`, `TextField`, `SelectableList`
- **Mod menu pieces:** `Scaffold`, `Section`, `Panel`, `Card`, `Dialog`, `Tabs`, `SegmentedControl`, `ToggleButton`
- **`GpuCanvas`:** draws large prepared RGBA images, like map tiles, from a GPU texture array, and swaps frames without recomposing
- **Theming:** `MinecraftTheme` holds colour and text roles, like `MaterialTheme`
- **Navigation:** a `NavHost` that renders Navigation 3 entries and closes on Escape through `BackHandler`

### A mod on KNH Core

A mod's code lives in `commonMain` and talks only to KNH Core. Each loader gets a one-line entrypoint that hands a `ModEntrypoint` to `Platform.initialize`:

```kotlin
object ExampleEntrypoint : ModEntrypoint {
    override val modId = "example"
    override val modName = "Example"
    override val modVersion = "1.0.0"

    // Both sides: configs, network channels, server events.
    override fun initialize() {
        ExampleConfig.register()
    }

    // Physical client only.
    override fun initializeClient() {
        ExampleCommand.register()
        KeyBindings.register("key.example.open", modId, Key.K) { Screens.open(ExampleScreen()) }
        ClientBackend.current.registerHud(ExampleHud)
    }
}

class ExampleScreen : ComposeMenuScreen() {
    @Composable
    override fun Content() {
        var count by remember { mutableStateOf(0) }
        Scaffold(screenWidth = width, screenHeight = height, title = "Example", subtitle = "Counter", onClose = ::close) {
            Text("Clicked $count times")
            Button("Click") { count++ }
        }
    }
}
```

The same code opens the same screen on every loader. The loader entrypoints are one line each:

```kotlin
// GTNH: from the @Mod object's pre-init handler
Platform.initialize(ExampleEntrypoint)
// Fabric: ModInitializer.onInitialize
override fun onInitialize() = Platform.initialize(ExampleEntrypoint)
// NeoForge: the @Mod object's constructor
init { Platform.initialize(ExampleEntrypoint) }
```

Each loader's manifest declares the dependency on `knhcore`: `required-after:forgelin;required-after:knhcore;` in the GTNH `@Mod`, `"knhcore": "*"` in `fabric.mod.json`, and a required `knhcore` entry in `neoforge.mods.toml`. `Platform.initialize` checks that the mod and KNH Core versions match.

### The common API

| Package | What it's for |
| --- | --- |
| `platform` | `ModEntrypoint`, `Platform` (loader, game and config directories, loaded mods) |
| `ui.compose.screen` | `ComposeScreen`, `ComposeMenuScreen`, `Screens.open` |
| `ui.compose.hud` | `HudLayer` for HUD elements, registered with `ClientBackend.registerHud` |
| `ui.compose.input` | `KeyBindings`, `Key`, `KeyPress` |
| `client` | `ClientBackend` (player, world id, dimension, pointer), `ClientCommand` |
| `event` | `ClientEvents` and `ServerEvents`: ticks, connect and disconnect, server start and stop, players |
| `config` | `ModConfig`: declared settings, stored as Forge `.cfg` on GTNH and `ModConfigSpec` TOML on 26.2, each with the loader's config screen |
| `network` | `ModChannel`: typed client ↔ server messages. Bad or foreign frames are dropped, never a disconnect |
| `serialization` | `JsonFileStorage` and `FrameworkJson` for mod files |
| `log` | `logger<T>()` over the loader's logging |
| `world` | `TileScanner` and `ChunkColumns` for top-down chunk scans, `TexelAverage` for texture colours |

Some things are per platform, because the games differ too much to share:

- **GTNH:** `WorldOverlay` draws in the world (lines, outlines, glass boxes and spheres, labels, markers that ghost through walls). `WorldScopedJsonStore` keeps per-world state. `BlockColors` and `BiomeTints` do map colours.
- **26.2:** `GlassGizmos` draws glass boxes and spheres through Minecraft's gizmos; everything else in the world uses vanilla gizmos directly. `BlockColors` and `BiomeTints` read block models and biome registries.

The `testgui` [storybook](../mods/testgui/) shows every component in its states: run `/testgui`. The [guide](GUIDE.md) covers layout, state, navigation, HUDs, settings, storage, testing and the internals in depth.

### Build

```bash
./gradlew :framework:buildAll
```

`src/commonMain` holds the Compose layer and the common API. `src/gtnhMain` is the Forge 1.7.10 integration. `src/modernMain` is shared by Fabric and NeoForge 26.2, which add only their own hooks in `src/fabricMain` and `src/neoforgeMain`.
