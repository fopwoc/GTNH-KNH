# KNH Core

The library under every KNH mod: real AndroidX Jetpack Compose for screens and HUDs, plus the plumbing a mod needs (configs, networking, key bindings, commands, storage), with one API on every loader KNH supports.

It does nothing on its own. Players only need it because the mods do.

## Install

Put the KNH Core jar for your loader in `mods/`, next to the mods that need it. Their versions must match; a mismatch is reported at startup. Supported loaders and Minecraft versions, and what else to install, are listed in the [main README](../README.md#install).

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
        Hud.register(ExampleHud)
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

The same code opens the same screen on every loader. Each loader's entrypoint is one call:

```kotlin
// GTNH: the @Mod object's pre-init handler
@Mod.EventHandler
fun onPreInit(event: FMLPreInitializationEvent) = Platform.initialize(ExampleEntrypoint)

// Fabric: a ModInitializer, declared with the kotlin adapter in fabric.mod.json
object ExampleFabric : ModInitializer {
    override fun onInitialize() = Platform.initialize(ExampleEntrypoint)
}

// NeoForge: the @Mod object's constructor, with Kotlin for Forge as the mod loader
@Mod("example")
object ExampleNeoForge {
    init { Platform.initialize(ExampleEntrypoint) }
}
```

Each manifest declares the dependency on `knhcore`: `required-after:forgelin;required-after:knhcore;` in the GTNH `@Mod`, `"knhcore": "${modVersion}"` in `fabric.mod.json`, and a required `knhcore` entry in `neoforge.mods.toml`. `Platform.initialize` checks that the mod and KNH Core versions match. The [guide](GUIDE.md#1-setting-up-a-mod) has the full manifests.

### The common API

| Package | What it's for |
| --- | --- |
| `platform` | `ModEntrypoint`, `Platform` (loader, game and config directories, loaded mods) |
| `ui.compose.screen` | `ComposeScreen`, `ComposeMenuScreen`, `Screens.open` |
| `ui.compose.hud` | `HudLayer` for HUD elements, registered with `Hud.register` |
| `ui.compose.input` | `KeyBindings`, `Key`, `KeyPress` |
| `client` | `ClientBackend` (in world, player position, world id, dimension, pointer), `ClientCommand` |
| `event` | `ClientEvents` and `ServerEvents`: ticks, connect and disconnect, server start and stop, players |
| `config` | `ModConfig`: declared settings, stored as Forge `.cfg` on GTNH and `ModConfigSpec` TOML on modern loaders, each with the loader's config screen |
| `network` | `ModChannel`: typed client ↔ server messages. Bad or foreign frames are dropped, never a disconnect |
| `serialization` | `JsonFileStorage` and `FrameworkJson` for mod files |
| `log` | `logger<T>()` over the loader's logging |
| `world` | `TileScanner` and `ChunkColumns` for top-down chunk scans, `TexelAverage` for texture colours |

Some things are per platform, because the games differ too much to share:

- **Drawing in the world.** GTNH has `WorldOverlay`: lines, outlines, glass boxes and spheres, labels, markers that ghost through walls. On modern loaders, `GlassGizmos` draws the same glass through Minecraft's gizmos, and lines and labels are vanilla gizmos.
- **In-world input** such as a middle click is the loader's own event or mixin.
- **Map colours.** `BlockColors` and `BiomeTints` exist on both, reading textures and biomes the way each game stores them.
- **GTNH extras:** `WorldScopedJsonStore` and `WorldScopedSync` for debounced per-world files, and `ComposeGuiScreen` for GTNH-only screens that need vanilla hooks.

The `testgui` [storybook](../mods/testgui/) shows every component in its states: run `/testgui`. The [guide](GUIDE.md) covers everything in depth, including where GTNH and modern loaders differ and how the renderer works underneath.

### Use it from another project

Development jars are published to a Maven repository on GitHub Pages, one artifact per loader: `knh-core-gtnh`, `knh-core-fabric-<minecraft>` and `knh-core-neoforge-<minecraft>`. Their POMs bring in Compose Runtime, Lifecycle, ViewModel, Navigation 3 and kotlinx.serialization, so the IDE resolves them without further setup. Apply the Compose compiler plugin at your Kotlin version: KNH Core's composables can only be called from code it compiles.

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    maven("https://fopwoc.github.io/GTNH-KNH/")
}

dependencies {
    compileOnly("io.github.fopwoc:knh-core-fabric-<minecraft>:<version>")
}
```

Use `compileOnly`, or your loader's equivalent: players install KNH Core as its own mod. The GTNH artifact is the MCP-named development jar, the one to compile against in a GTNHGradle project.

### Build

```bash
./gradlew :framework:buildAll
./gradlew :framework:publishMod    # into framework/build/maven, or -PmavenRepository=<dir>
```

`src/commonMain` holds the Compose layer and the common API. `src/gtnhMain` is the GTNH integration. `src/modernMain` is shared by Fabric and NeoForge, which add only their own hooks in `src/fabricMain` and `src/neoforgeMain`.
