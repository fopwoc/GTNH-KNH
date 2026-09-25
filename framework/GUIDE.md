# KNH Core developer guide

KNH Core lets you write mod GUIs and HUD overlays with real Jetpack Compose, once, for GTNH 1.7.10, Fabric 26.2 and NeoForge 26.2. The Compose *runtime* (composition, state, effects, `remember`, coroutines) drives KNH's own layout engine, which draws with the game's GUI primitives: `FontRenderer` and `Gui` on GTNH, the GUI render state on 26.2. There is no Compose UI or Skia involved. Compose state and effect patterns transfer; the layout and input differences are listed in [Differences from Android Compose](#differences-from-android-compose).

This guide covers the framework's main APIs, in the order you will need them. Snippets use the packages under `io.github.fopwoc.mods.framework`; example names such as `MyScreen` and `MyConfig` stand for your mod's code. Everything is common code unless a section says **GTNH** or **26.2**.

---

## Table of contents

1. [Setting up a mod](#1-setting-up-a-mod)
2. [Your first screen](#2-your-first-screen)
3. [Layout](#3-layout)
4. [Modifiers](#4-modifiers)
5. [Text and styled text](#5-text-and-styled-text)
6. [Controls](#6-controls)
7. [Lists and scrolling](#7-lists-and-scrolling)
8. [State, ViewModels and coroutines](#8-state-viewmodels-and-coroutines)
9. [Navigation](#9-navigation)
10. [HUD overlays](#10-hud-overlays)
11. [Input, focus and the back key](#11-input-focus-and-the-back-key)
12. [Settings with ModConfig](#12-settings-with-modconfig)
13. [Saving data as JSON](#13-saving-data-as-json)
14. [Client ↔ server messages](#14-client--server-messages)
15. [Units, colours and tokens](#15-units-colours-and-tokens)
16. [Testing](#16-testing)
17. [How it works under the hood](#17-how-it-works-under-the-hood)
18. [Differences from Android Compose](#differences-from-android-compose)
19. [Cookbook](#cookbook)

---

## 1. Setting up a mod

A mod is a [KnhMP](../knhmp/README.md) module in this repository. Loader-independent code lives in `src/commonMain`. `src/gtnhMain` holds the GTNH side, `src/modernMain` what Fabric and NeoForge 26.2 share, and `src/fabricMain` and `src/neoforgeMain` as little as possible: ideally just the loader's entrypoint.

`build.gradle.kts` (the `mods/*` modules are the reference; this one targets GTNH only, see Measure's for all three loaders):

```kotlin
plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)   // required: it rewrites @Composable functions
}

knhmp {
    modId = "mymod"
    modName = "My Mod"
    modGroup = "io.github.fopwoc.mods.mymod"

    sourceSets {
        commonMain { jvmTarget = libs.versions.jvmBytecode.get().toInt() }
        gtnhMain { dependsOn(commonMain) }
    }

    dependencies {
        implementation(projects.framework)   // KNH Core and its bundled Compose/lifecycle/serialization API
    }

    targets {
        gtnh {
            kotlin { stdlibVersion = libs.versions.gtnhKotlinStdlib.get() }
            plugins {
                alias(libs.plugins.gtnh.convention)
                alias(libs.plugins.compose.compiler)
            }
            dependencies { implementation(libs.forgelin) }
        }
    }
}
```

KNH Core ships the Compose, lifecycle, navigation and serialization libraries inside its own jar; a mod only compiles against them and never bundles them again. The Kotlin standard library and coroutines come from the loader's Kotlin adapter: Forgelin on GTNH, Fabric Language Kotlin on Fabric, Kotlin for Forge on NeoForge.

The mod's startup is a `ModEntrypoint` in common code. `KnhMP` generates `ModMetadata` with the mod's identity:

```kotlin
object MyEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initialize() {          // both sides
        MyConfig.register()
        ServerEvents.tickEnd.subscribe { MyService.tick() }
    }

    override fun initializeClient() {    // physical client only
        ClientEvents.tickEnd.subscribe { MyHud.tick() }
    }
}
```

Each loader's source set only hands it to `Platform.initialize`, at the earliest point the loader allows registrations, and declares the dependency on `knhcore` in its manifest.

**GTNH (Forge 1.7.10).** The manifest is the `@Mod` annotation itself; call from pre-init:

```kotlin
@Mod(
    modid = MOD_ID, name = MOD_NAME, version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;",
    acceptableRemoteVersions = "*",          // client-side mod: any server is fine
)
object MyMod {
    @Mod.EventHandler
    fun onPreInit(event: FMLPreInitializationEvent) = Platform.initialize(MyEntrypoint)
}
```

**Fabric.** A `ModInitializer` object, declared in `fabric.mod.json` with the `kotlin` adapter:

```kotlin
object MyFabric : ModInitializer {
    override fun onInitialize() = Platform.initialize(MyEntrypoint)
}
```

```json
"entrypoints": {
  "main": [{ "adapter": "kotlin", "value": "com.example.mymod.MyFabric" }]
},
"contact": {
  "homepage": "${repositoryUrl}",
  "sources": "${repositoryUrl}",
  "issues": "${issuesUrl}"
},
"depends": {
  "fabric-api": "*",
  "fabric-language-kotlin": ">=1.14",
  "knhcore": "${modVersion}",
  "minecraft": "${minecraftVersion}"
}
```

Client-only mods add `"environment": "client"`.

**NeoForge.** Nothing like 1.7.10 Forge: no event handlers and no `@Mod` parameters. The `@Mod` object's constructor is the entry, and Kotlin for Forge is the language loader:

```kotlin
@Mod(ModMetadata.MOD_ID)
object MyNeoForge {
    init { Platform.initialize(MyEntrypoint) }
}
```

```toml
# META-INF/neoforge.mods.toml
modLoader = "kotlinforforge"
loaderVersion = "[6,)"
issueTrackerURL = "${issuesUrl}"

[[mods]]
modId = "${modId}"
version = "${modVersion}"
displayURL = "${repositoryUrl}"

[[dependencies.${modId}]]
modId = "knhcore"
type = "required"
versionRange = "[${modVersion}]"                # the mod and KNH Core always ship together
ordering = "AFTER"
side = "BOTH"                                 # CLIENT for client-only mods
```

NeoForge constructs mods in parallel, so `initialize` of two mods can run at the same time on different threads. KNH Core's registrations are safe for that; state your own mods share should be too. KnhMP fills in `${modId}`, `${modName}`, `${modVersion}` and `${minecraftVersion}` in every manifest, plus `${repositoryUrl}` and `${issuesUrl}` derived from the Git `origin` remote. Requiring KNH Core at exactly `${modVersion}` lets the loader report a mismatch before anything runs.

`Platform.initialize` logs the startup, checks that the mod was built for the installed KNH Core version and runs `initializeClient` only in the physical client, so client-only classes referenced from there are never loaded on a dedicated server.

Common events (`io.github.fopwoc.mods.framework.event`):

| Event | Fires |
| --- | --- |
| `ClientEvents.tickStart` / `tickEnd` | around every client tick, client thread |
| `ClientEvents.connected` / `disconnected` | when a client world appears or goes away (not on dimension changes), client thread |
| `ServerEvents.tickStart` / `tickEnd` | around every server tick, server thread |
| `ServerEvents.started` / `stopping` | server lifecycle |
| `ServerEvents.playerJoined` / `playerLeft` | with a `GamePlayer(id, name)` |

`Platform` also answers `loader`, `minecraftVersion`, `isClient`, `gameDirectory`, `configDirectory` and `isModLoaded(id)`.

---

## 2. Your first screen

Extend `ComposeScreen` and implement `Content()`:

```kotlin
class HelloScreen : ComposeScreen() {
  @Composable
  override fun Content() {
    var clicks by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
      Panel(modifier = Modifier.width(220.uu).align(Alignment.Center)) {
        Column(verticalArrangement = VerticalArrangement.spacedBy(6.uu)) {
          Text("Hello from Compose Runtime")
          Text("Clicked $clicks times", style = TextStyle(color = MinecraftColor.Gray.color))
          Button(text = "Click me", modifier = Modifier.fillMaxWidth()) { clicks++ }
          Button(text = "Close", modifier = Modifier.fillMaxWidth()) { close() }
        }
      }
    }
  }
}
```

Open it with `Screens.open(HelloScreen())` from anywhere on the client: a command, a key binding, a network handler. It opens on the next client tick, outside whatever handler asked, and only while the player is in a world. Each platform shows it in its native screen.

`ComposeScreen` has:

- `background`: `ComposeBackgroundStyle.VanillaDefault` (the dimmed vanilla background), `ComposeBackgroundStyle.None`, or `ComposeBackgroundStyle.Color(Color(0xA0101010))`, the default
- `pausesGame`: whether singleplayer pauses while it's open, `true` by default
- `width` and `height`: the GUI-scaled size, as snapshot state, so `Content` recomposes on resize
- `close()`: closes on the next tick, so a click handler may call it
- hooks: `onTick()` every client tick, `onFrame()` every rendered frame, `onScroll(x, y, notches)` before Compose sees the wheel, `onUnhandledKey(press)` for keys no text field, `BackHandler` or `NavHost` consumed (return `true` to swallow the key before Escape closes the screen), and `onClosed()`

Input events arrive at tick rate on some platforms, so a continuous gesture such as dragging a map samples `ClientBackend.current.pointerX/Y` and `isMouseButtonDown` in `onFrame` instead. Palimpsest's map screen is the worked example.

### A mod menu in three lines each

Most mod screens are the same shape: no pause, no dimmed background, close on the key that opened them, and re-read some runtime state every tick. That is `ComposeMenuScreen`; wiring the key binding and the chat command is `KeyBindings` and `ClientCommand`:

```kotlin
class MyMenuScreen : ComposeMenuScreen(toggleKey = MyKeys.openMenu) {
  @Composable
  override fun Content() = MyMenuRoute(width, height, refreshToken = refreshToken, onClose = ::close)
}

object MyKeys {
  lateinit var openMenu: KeyBinding
  fun register() {
    // The name is a translation key; the category is the mod id.
    openMenu = KeyBindings.register("key.mymod.openMenu", MOD_ID, Key.M) { Screens.open(MyMenuScreen()) }
  }
}

object MyCommand : ClientCommand(name = "mymod", usage = "/mymod | /mymod reset") {
  override fun run(args: List<String>): String? = when (args.firstOrNull()) {
    null -> { Screens.open(MyMenuScreen()); null }   // null = no chat reply
    "reset" -> { MyState.reset(); "Reset" }
    else -> usage
  }
  override fun complete(args: List<String>) = if (args.size == 1) listOf("reset") else emptyList()
}
```

Call `MyKeys.register()` and `MyCommand.register()` from `initializeClient`. Leave the default key out (`null`) to ship the binding unbound. Key names are translation keys: on GTNH the category shows as `key.categories.<category>`, on 26.2 as `key.category.<category>.main`, so give both a line in the mod's language files. `KeyBinding.isDown` and `KeyBinding.matches(press)` read the player's current binding.

`refreshToken` increments every tick; key a `LaunchedEffect(refreshToken)` on it in the route to poll non-Compose state. Override `onUnhandledKey` for extra shortcuts and call `super` first so the toggle key keeps working; `refreshNow()` re-reads before the next tick.

For the look, `Scaffold` (centred panel, title and subtitle, Close), `Section` (titled card), `Card` and `Dialog` (a small message with one button, for "cannot open" cases) are plain components in `ui.compose.component` that read [`MinecraftTheme`](#theme), like Material's `Scaffold` and `Card` read `MaterialTheme`. Don't want the theme? Build your own from `Panel`, `Column` and `Text`.

What every screen gets from the platform host:

- one Compose runtime and a `ViewModelStore`; a resize **reuses** the composition, closing disposes it and clears view models
- the runtime is pumped before every input event and every frame, so state written by a click is visible in the same frame
- mouse and keyboard input go to the topmost element under the cursor
- tooltips for anything with `Modifier.tooltip`
- Escape becomes a back event (see [Input, focus and the back key](#11-input-focus-and-the-back-key)) before it closes the screen

**GTNH:** the host is `GtnhComposeScreenHost`, built on `ComposeGuiScreen`, a vanilla `GuiScreen`. Extending `ComposeGuiScreen` directly is possible when a GTNH-only screen needs vanilla hooks, such as mixing in legacy `GuiButton`s through `drawComposeFallback()`.

---

## 3. Layout

The three containers behave like their Compose namesakes.

### Box

```kotlin
Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
  Text("corner")
  Text("centered", modifier = Modifier.align(Alignment.Center))
  Panel(modifier = Modifier.matchParentSize()) { /* stretches to the box */ }
}
```

`BoxScope` gives children `Modifier.align(Alignment)`, `matchParentWidth()`, `matchParentHeight()`, `matchParentSize()`. The `Alignment` companion has the nine usual constants (`TopStart` … `BottomEnd`).

### Column and Row

```kotlin
Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = VerticalArrangement.spacedBy(4.uu),
    horizontalAlignment = HorizontalAlignment.START,
) {
  Text("first")
  Text("centered", modifier = Modifier.align(HorizontalAlignment.CENTER))
  Spacer(modifier = Modifier.weight(1f))         // pushes the rest down
  Button(text = "bottom", modifier = Modifier.fillMaxWidth()) {}
}

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
    verticalAlignment = VerticalAlignment.CENTER,
) {
  Text("label", modifier = Modifier.weight(1f))
  Button(text = "OK", modifier = Modifier.width(60.uu)) {}
}
```

Arrangements: `Top/Center/Bottom` (`Start/Center/End` for rows), `SpaceBetween`, `SpaceAround`, `SpaceEvenly`, `spacedBy(space, alignment)`. `ColumnScope`/`RowScope` give `Modifier.weight(weight, fill = true)` and `Modifier.align(...)` on the cross axis.

### Spacer

`Spacer(width = 8.uu, height = 8.uu)` — or `Spacer(modifier = Modifier.weight(1f))` inside a Column/Row.

### Sizing rules

- Every element has a *natural* size (text width, button 98×20 minimum, …). `Modifier.width/height` fix it, `fillMaxWidth/Height/Size` stretch it to the parent, otherwise the natural size wins, clamped to the parent.
- A `Column` without a height is as tall as its content; with `fillMaxHeight()` or a fixed height it becomes a fixed box and children may overflow (nothing is clipped unless it scrolls).
- Weights distribute the remaining main-axis space after unweighted children are measured, like Compose.
- Padding is inside the element's bounds (background and border are drawn around the padded content).

### GPU canvas

`GpuCanvas` takes layout space like any other composable and draws a prepared `GpuCanvasFrame`. Each `GpuImageDraw` places an immutable `GpuImage` at a rectangle in canvas-local GUI coordinates. The GL backend caches images in a texture array and draws the quads in instanced batches. It uploads an image again only when its `GpuImage` instance changes. Pixels are RGBA8, from the top row down. Images in one frame currently share dimensions; a whole image can be drawn as one quad.

```kotlin
val image = remember { GpuImage(32, 32, rgbaBytes) }
val frame = GpuCanvasFrame(listOf(GpuImageDraw(image, 0f, 0f, 64f, 64f)))
GpuCanvas(frame, modifier = Modifier.width(64.uu).height(64.uu))
```

For a changing image, remember a `GpuCanvasState` and submit new frames to it. Submission replaces the frame read at draw time without recomposing or laying out the canvas. If several frames arrive before a draw, the latest one wins. `GpuCanvas(frame)` remains convenient when the frame changes as part of ordinary Compose UI state.

```kotlin
val canvas = remember { GpuCanvasState(initialFrame) }
GpuCanvas(canvas, modifier = Modifier.width(64.uu).height(64.uu))
// In a producer or frame callback: canvas.submit(nextFrame)
```

Keep `GpuImage` instances stable while their pixels stay the same. Prepare expensive world or disk data outside composition, then have a higher-level composable select the visible images, compute their positions, and choose an LOD. The [Test GUI GPU canvas story](../mods/testgui/) demonstrates a grid controller that owns pan, zoom and LOD, and Palimpsest's map is the full-size example. The GPU canvas has no map or grid policy.

**GTNH:** images live in a texture array drawn with instanced OpenGL 3.3 calls. **26.2:** images live in the cells of one atlas texture uploaded through the game's device API, so the GUI renderer batches a frame into one draw on OpenGL and Vulkan alike.

---

## 4. Modifiers

`Modifier` is an immutable chain; order matters only for `then` conflicts (last one wins per kind).

| Modifier | Effect |
| --- | --- |
| `padding(all)`, `padding(horizontal, vertical)`, `padding(left, top, right, bottom)` | Inset content. |
| `fillMaxWidth()`, `fillMaxHeight()`, `fillMaxSize()` | Stretch to the parent. |
| `width(uu)`, `height(uu)`, `size(uu)`, `size(w, h)` | Fixed size. |
| `background(color)` | Filled rectangle behind the element. |
| `border(color)` | 1 px border inside the bounds. |
| `tooltip(text)`, `tooltip(lines)`, `tooltip(styledText)` | Vanilla hover tooltip over the element. |
| `offset(x, y)` | Shift after placement (does not affect siblings). |
| `clickable(enabled) { }` | Left-click anywhere in the element's bounds; children with their own input still win. |
| `hoverBackground(color)` | Background painted only while the mouse is over the element. |
| `verticalScroll(state)`, `horizontalScroll(state)` | Make a `Column`/`Row` scroll — see [Lists and scrolling](#7-lists-and-scrolling). |
| `weight(w, fill)`, `align(...)`, `matchParent*()` | Scope modifiers from the parent container. |

Modifiers are values: you can keep them in constants and reuse them.

---

## 5. Text and styled text

```kotlin
Text("Plain", style = TextStyle(color = Color(0xFFE6E6E6), shadow = true))
Text("Wrapped paragraph …", modifier = Modifier.fillMaxWidth(), style = TextStyle(wrap = true))
Text("Right", modifier = Modifier.width(80.uu), style = TextStyle(alignment = HorizontalAlignment.END))
```

`TextStyle(color, shadow = true, alignment, wrap = false)`. A wrapped text needs a width constraint (`fillMaxWidth()` or a parent width) to wrap against.

Minecraft formatting is available through `StyledText`, which renders to `§` codes:

```kotlin
val label = styledText {
  withColor(MinecraftColor.Gold) { +"Warning: " }
  withBold { +"low TPS" }
  +" on "
  withItalic { withColor(MinecraftColor.Aqua) { +"Nether" } }
}
Text(label)
Button(text = label) {}
```

`MinecraftColor` is the vanilla 16-colour enum (each has a `.color` you can use in `TextStyle`). `StyledText.of("plain")` wraps a string; `plainText` and `formattedString` are precomputed properties. Raw `§` codes inside plain strings also work everywhere a string is drawn.

---

## 6. Controls

All controls are drawn by the framework. Buttons, checkboxes and sliders use the vanilla widget textures (the `widgets.png` sheet on GTNH, the `widget/*` GUI sprites on 26.2) and the vanilla click sound, so they look and feel exactly like the rest of the game while behaving as ordinary layout elements (any size, any modifier, no widget instances to keep in sync).

```kotlin
Button(text = "Apply", modifier = Modifier.fillMaxWidth(), enabled = dirty) { save() }

Checkbox(label = "Show HUD", checked = showHud) { showHud = it }

ToggleButton(label = "Sound", checked = sound) { sound = it }        // renders "Sound: ON"

Slider(
    value = volume, onValueChange = { volume = it },
    valueRange = 0.0..1.0, label = "Volume", suffix = "%", showDecimal = false,
)

SegmentedControl(options = Mode.entries, selected = mode, labelOf = { it.title }) { mode = it }

Tabs(options = Page.entries, selected = page, onSelected = { page = it }) { current ->
  when (current) { Page.General -> GeneralPage(); Page.Advanced -> AdvancedPage() }
}
```

All controls are *controlled*: they show the state you pass and report changes through the callback; store the value in a `mutableStateOf` (or a ViewModel) and pass it back in. `SegmentedControl` highlights the selected option (bold + yellow by default, override `selectedLabelOf`).

### TextField

```kotlin
val name = rememberTextFieldState("Steve")
TextField(state = name, modifier = Modifier.fillMaxWidth(), placeholder = "Name", style = TextFieldStyle(maxLength = 16))
Text("Length ${name.text.length}, selection ${name.selection.min}..${name.selection.max}")
Button(text = "Focus") { name.requestFocus() }
```

`TextFieldState` exposes `text`, `selection: TextRange`, `focused`, plus `requestFocus()`, `clearFocus()`, `edit(text, selection)`, `selectAll()`, `placeCursorAtEnd()`. Assigning `text` keeps the selection in range. Editing supports typing, Backspace/Delete (Ctrl = word), arrows/Home/End with Shift-extend, Ctrl+A/C/X/V, click-to-place and drag-select, horizontal scrolling and a blinking cursor. Escape blurs the field; clicking outside blurs it; a field removed from composition loses focus automatically.

---

## 7. Lists and scrolling

### Scrollable Column/Row

```kotlin
val scroll = rememberScrollState()
Column(
    modifier = Modifier.fillMaxWidth().height(120.uu).verticalScroll(scroll),
    verticalArrangement = VerticalArrangement.spacedBy(2.uu),
) {
  entries.forEach { Text(it) }
}
```

The container clips its viewport, draws a slim scrollbar when the content overflows, and handles the mouse wheel (over the area) and thumb dragging. `ScrollState` has `value`, `maxValue`, `scrollBy(delta)`, `scrollTo(offset)`. All children are composed and measured; use this for short content.

### LazyColumn

```kotlin
val listState = rememberLazyListState()
LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
  item { Text("Header") }
  items(rows) { row -> Text(row.label) }
  itemsIndexed(rows) { index, row -> Row { Text("$index"); Text(row.label) } }
}
Button(text = "Jump to 500") { listState.scrollToItem(500) }
```

Only the items around the visible window are composed. Items may have any height: each composed item is measured naturally and its height remembered per index, and the running average stands in for rows not seen yet — so the scrollbar is an estimate until the list has been scrolled through once, and `scrollToItem` on unmeasured territory lands approximately first and settles over the next layouts. Pass `itemHeight = 12.uu` when every row is the same height: positions are then exact from the start and layout is cheaper. `LazyListState` exposes `firstVisibleItemIndex`, `visibleItemCount`, `scrollOffset`, `scrollToItem`, `scrollBy`.

### SelectableList and MultiSelectableList

```kotlin
SelectableList(items = names, selectedIndex = selected, rowHeight = 12.uu, visibleRowCount = 8) { selected = it }

MultiSelectableList(items = names, selectedIndices = selectedSet) { selectedSet = it }
```

Rows are strings; the list scrolls itself (wheel + thumb), highlights hover and selection. The multi variant follows desktop conventions: click selects one, Ctrl+click toggles, Shift+click extends from the last click, Ctrl+Shift+click adds a range.

---

## 8. State, ViewModels and coroutines

Compose state works unchanged: `remember`, `mutableStateOf`, `mutableIntStateOf`, `derivedStateOf`, `mutableStateListOf`, `LaunchedEffect`, `DisposableEffect`, `SideEffect`, `rememberUpdatedState`, `key`, `CompositionLocalProvider`.

### ViewModels

Every screen's host is a `ViewModelStoreOwner`; the AndroidX `viewModel()` API is available:

```kotlin
class SettingsViewModel : ViewModel() {
  private val _state = MutableStateFlow(SettingsModel())
  val state: StateFlow<SettingsModel> = _state

  fun toggle() { _state.update { it.copy(enabled = !it.enabled) } }
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = viewModel(SettingsViewModel::class)) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  SettingsView(state, onToggle = viewModel::toggle)
}
```

Use the no-argument `viewModel(Class)` form with a no-arg constructor (a `NewInstanceFactory` is installed), or `viewModel { SettingsViewModel(dependency) }` with a factory lambda. View models survive a window resize and are cleared when the screen closes. Inside a `NavHost`, each Navigation 3 `contentKey` gets an owner that is cleared when the key leaves the back stack.

`collectAsStateWithLifecycle` is the framework's own (`ui.compose.runtime`); it stops collecting while the owner is below `STARTED`, which happens for covered navigation entries.

### Coroutines

`LaunchedEffect`, `rememberCoroutineScope()` and `viewModelScope` all dispatch on the client thread through the runtime's main dispatcher; `withFrameNanos` resolves once per rendered frame. Long work belongs on `Dispatchers.IO`/`Default` — switch back with `withContext(Dispatchers.Main)` before touching Minecraft. Exceptions in effects or the recomposer are logged and rethrown on the next frame so they show up as a normal crash report instead of a frozen screen.

### Screen-scoped state

There is no `SaveableStateRegistry` at the screen root, so `rememberSaveable` behaves like `remember` there. Inside `NavHost`, Navigation 3's `SaveableStateHolderNavEntryDecorator` retains a scroll position or text draft when an entry is covered and removes it when the entry is popped.

---

## 9. Navigation

**This is the real `androidx.navigation3:navigation3-runtime` library.** Use Navigation 3's `NavKey`, `NavBackStack`, `NavEntry`, and `entryProvider` directly. KNH has removed its former back stack, saver, navigator, and entry DSL. Its `NavHost` displays Navigation 3 entries through KNH's renderer, applies Navigation 3's saveable-state decorator, scopes ViewModels and lifecycle by `contentKey`, and handles Escape. Navigation 3's `NavDisplay` requires Compose UI and cannot render KNH nodes.

```kotlin
sealed interface Dest : androidx.navigation3.runtime.NavKey {
  data object Home : Dest
  data class Details(val id: Long) : Dest
}

@Composable
fun App() {
  val backStack = remember { androidx.navigation3.runtime.NavBackStack<Dest>(Dest.Home) }

  NavHost(
      backStack = backStack,
      entryProvider = androidx.navigation3.runtime.entryProvider {
        entry<Dest.Home> {
          HomeRoute(onOpen = { backStack.add(Dest.Details(it)) })
        }
        entry<Dest.Details> { key ->
          DetailsRoute(id = key.id, onBack = { backStack.removeAt(backStack.lastIndex) })
        }
      },
      handleBack = true,                                   // Escape pops while canPop
      emptyContent = { Text("nothing here") },
  )
}
```

Change the stack with normal list operations. Each `contentKey` gets its own `ViewModelStore` and lifecycle (`RESUMED` on top, `STARTED` when covered, destroyed when removed). Navigation 3's saveable-state decorator retains `rememberSaveable` state while an entry is covered.

Navigation 3's default `contentKey` is derived from the destination key. Repeated equal keys therefore share entry state; put a unique identifier in the destination key when separate state is needed. For a stack that survives restoration, use Navigation 3's `rememberNavBackStack` with serializable keys and a `SavedStateConfiguration`.

---

## 10. HUD overlays

A `HudLayer` is a composition drawn over the game while it's `visible`, without a screen. Register it once from `initializeClient`; layers draw in registration order:

```kotlin
object MyHud : HudLayer("mymod:hud") {
  private var model by mutableStateOf(HudModel())

  override val visible: Boolean get() = MyConfig.showHud && MyState.active

  // Every frame before drawing, on the render thread.
  override fun beforeFrame() {
    model = computeModel()   // same value → no recomposition
  }

  @Composable
  override fun Content() {
    Box(modifier = Modifier.fillMaxSize()) {
      HudAnchor(bounds = HudRect(left = 4, top = 4, width = 120, height = 40), contentAlignment = Alignment.TopStart) {
        Column(modifier = Modifier.background(Color(0x80000000)).padding(4.uu)) {
          Text(model.title)
        }
      }
    }
  }
}

// initializeClient():
Hud.register(MyHud)
```

- `width` and `height` are the GUI-scaled screen size, as snapshot state.
- The composition is released while the layer is hidden and built again when it shows.
- `HudAnchor(bounds, contentAlignment)` positions a box at screen coordinates; children align inside it. `ClientBackend.current.playerListBounds(width)` gives the player list's bounds where the platform knows them (GTNH), for cards that sit under it like TPS Tab's.
- Layers receive no keyboard or mouse input; they are display-only.
- A HUD layer and a screen can coexist, e.g. Tab held while a menu is open.
- **Threading:** network and connection callbacks may arrive on network threads. Never touch Compose state from them; handlers of a `ModChannel` already run on the game thread, and `ClientEvents` fire on the client thread.

---

### Drawing in the world

In-world drawing is per platform, because the renderers have nothing in common.

**GTNH:** boxes, spheres, lines and labels at world positions (Measure's shapes, Hotspot's chunk columns) go through `WorldOverlay` from `RenderWorldLastEvent`. It sets the overlay GL state (no texture, lighting, depth or culling; blending on), gives you world coordinates (the camera offset is applied for you, and follows the render view entity so Freecam works), and draws labels after all shapes:

```kotlin
@SubscribeEvent
fun onRenderWorld(event: RenderWorldLastEvent) =
    WorldOverlay.render(event.partialTicks) {
      glassBox(x, y, z, x + 3.0, y + 2.0, z + 3.0, color)          // translucent, glowing rims; edges shown from inside
      glassSphere(cx, cy, cz, radius, color, grid = GlassGrid.INSIDE) // lat/long grid + eye-level ring inside (or ALWAYS / OFF)
      line(x1, y1, z1, x2, y2, z2, color, width = 2f)
      blockOutline(bx, by, bz, color, width = 2f)                    // wire, for single blocks
      ghosted { hiddenAlpha ->                                        // depth-aware: full in front, ghost behind terrain
        val c = hiddenAlpha?.let { color.copy(alpha = it) } ?: color
        filledBox(bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0, c.copy(alpha = c.alpha / 4))
        cornerBrackets(bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0, c, width = 2f, grow = pulse)
      }
      label(x + 1.5, y + 2.5, z + 1.5, listOf("3 × 2 × 3", "detail"), color)
      camera.eyeX                                                     // where the viewer looks from
    }
```

Glass boxes are drawn without a depth test, so the whole shape is visible through terrain and from inside. Glass spheres are drawn twice: at full strength where they are in front of terrain, and faded where terrain hides them, so where the shell cuts into the ground shows. The rim alpha is what outlines glass, and the rim is computed from the eye, not the feet (`RenderWorldLastEvent`'s origin). Spheres are batched one latitude band at a time — GTNH's patched Tessellators overflow on a whole sphere and draw confetti. `ghosted` is the one depth-aware helper, for small markers such as anchors; inflate a marker by ~0.02 blocks so it does not z-fight the block faces. Labels are billboards drawn at full brightness (the font goes through the lightmap and would be black inside blocks otherwise). Minecraft's alpha test (`> 0.1`) is disabled for the whole pass, since glass fills sit below it.

**26.2:** world drawing goes through Minecraft's gizmos, which render on OpenGL and Vulkan. Collect them during level render-state extraction (Fabric's `LevelExtractionEvents`, NeoForge's `ExtractLevelRenderStateEvent`) and hand them to the level renderer:

```kotlin
val collector = SimpleGizmoCollector()
Gizmos.withCollector(collector).use {
  GlassGizmos.box(Vec3(x, y, z), Vec3(x + 3.0, y + 2.0, z + 3.0), color, eye)
  GlassGizmos.sphere(Vec3(cx, cy, cz), radius, color, eye, GlassGrid.INSIDE)
  Gizmos.line(from, to, color.argbInt, 2f).setAlwaysOnTop()
}
Minecraft.getInstance().levelRenderer.addMainThreadGizmos(collector.drainGizmos())
```

`GlassGizmos` is GTNH's glass on gizmos: the same rims, lighting and alphas, with the sphere ghosted behind terrain in the same way. Gizmo quads take one colour each, so the gradients are approximated with finer tessellation and banded rims. Lines, outlines and labels are vanilla gizmos; `setAlwaysOnTop()` draws over terrain, and `TextGizmo`'s scale is divided by 16 when drawn. Measure's `MeasurementWorldCanvas` puts both platforms behind one interface.

---

## 11. Input, focus and the back key

- Clicks are dispatched to the topmost input target under the cursor: buttons, checkboxes, sliders, lists, text fields, scroll thumbs. Drags continue to the element that captured the press (sliders, scrollbars, text selection).
- The mouse wheel goes to the innermost scrollable under the cursor.
- Keyboard input goes to the focused `TextField`; otherwise Escape is offered to `BackHandler`s (innermost first), then to `NavHost`, and finally closes the screen.

```kotlin
BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }
```

`BackHandlerResult { consumed }` is the variant whose callback decides whether the event was handled. Handlers nest: the innermost enabled one runs first, then `NavHost(handleBack = true)`, then the screen closes.

Modifier keys during a click are available to the framework's own controls (multi-select). `KeyBindings.isDown(key)` reads a raw key anywhere on the client. Custom in-world input (like Measure's middle-click) is the loader's own business and unrelated to the GUI layer: Forge `MouseEvent`/`KeyInputEvent` on GTNH, `InputEvent` on NeoForge, a mixin on Fabric.

---

## 12. Settings with ModConfig

`ModConfig` declares typed, normalized settings once in common code. Each platform stores them in its native format and gives you its native settings screen:

| Platform | File | Screen |
| --- | --- | --- |
| GTNH 1.7.10 | `config/<name>.cfg` (Forge) | **Mods → Config** (`GuiConfig`) |
| NeoForge 26.2 | `config/<name>.toml` (`ModConfigSpec`) | Mod list config button |
| Fabric 26.2 | `config/<name>.toml` via Forge Config API Port | Mod Menu **Configure** |

```kotlin
object MyConfig : ModConfig(modId = MOD_ID, name = "my_mod") {
  val enabled by boolean("enabled", default = true, comment = "Master switch.")
  val interval by int("interval", default = 20, min = 1, max = 1200, comment = "Ticks between updates.")
  val stale by int("stale", default = 60, min = 1, comment = "…", normalize = { it.coerceAtLeast(interval * 2) })
  val scale by double("scale", default = 1.0, min = 0.5, max = 2.0, comment = "…")
  val alignment by enum("alignment", default = Side.LEFT, comment = "…")           // stored lower-case on GTNH
  val ids by string("ids", default = "", comment = "…", normalize = { it.trim() })

  override fun onLoaded() { /* derive caches from the normalized values */ }
}
```

Call `MyConfig.register()` once from `initialize` (on NeoForge that is mod construction, which is where it has to happen). Nothing else is needed: in-game edits and edits to the file while the game runs are picked up by the platform. `scope` (`CLIENT` by default, `COMMON`, `SERVER`) maps to NeoForge's config types; GTNH treats them alike.

**GTNH** still needs the class names Forge's `@Mod(guiFactory = …)` asks for:

```kotlin
@SideOnly(Side.CLIENT)
class MyGuiFactory : ConfigGuiFactory() {
  override fun screenClass() = MyConfigScreen::class.java
}
@SideOnly(Side.CLIENT)
class MyConfigScreen(parent: GuiScreen) : ConfigScreen(parent, MyConfig, "My Mod configuration")
```

Behaviour: values are read, normalized **in declaration order** (a normalizer may read settings declared above it) and written back when normalization changed them. `revision` increments per load so caches can key on it. Language keys are `config.<modid>.<key>`, with `.tooltip` for hover text, on every platform. Integer settings may pass `hint = { value -> "…" }`, shown beside the field on GTNH.

---

## 13. Saving data as JSON

For mod *state* (not settings) use kotlinx.serialization through `JsonFileStorage`:

```kotlin
@Serializable data class Bookmarks(val version: Int = 1, val entries: List<Bookmark> = emptyList())

val file = JsonFileStorage.modConfigFile(Platform.gameDirectory, MOD_ID, "bookmarks", "$contextId.json")   // config/<modid>/bookmarks/…
val loaded = JsonFileStorage.readOrDefault(file, FrameworkJson.prettyConfig, ::Bookmarks) { logger.warn("bad file", it) }
JsonFileStorage.write(file, loaded.copy(entries = entries), FrameworkJson.prettyConfig)
```

`FrameworkJson.prettyConfig` is the shared `Json` (pretty, ignores unknown keys, encodes defaults). Writes are synchronous: debounce them from a tick handler if they can happen many times per second, and flush on `ClientEvents.disconnected`.

### Per world or server

State that belongs to the world the player is in (saved measurements, the last profile) is keyed on `ClientBackend.current.currentWorldId`: `singleplayer-<world>` or `server-<address>`, file-name safe, and null outside a world. Load when it changes, write when it goes away:

```kotlin
private var loadedFor: String? = null

fun tick() {   // from ClientEvents.tickEnd
  val id = ClientBackend.current.currentWorldId
  if (id == loadedFor) return
  loadedFor?.let(::save)
  loadedFor = id
  state = id?.let(::load) ?: Bookmarks()
}
```

**GTNH:** `WorldScopedJsonStore` + `WorldScopedSync` package that pattern with debounced writes:

```kotlin
val store = WorldScopedJsonStore(MOD_ID, "bookmarks", Bookmarks.serializer(), ::Bookmarks)

private val sync = WorldScopedSync(
    store = store,
    debounceTicks = 20,
    onLoaded = { loaded -> state.replaceAll(loaded?.entries.orEmpty()) },   // null = no world
    snapshot = { Bookmarks(entries = state.entries) },
)

@SubscribeEvent fun onClientTick(e: TickEvent.ClientTickEvent) { if (e.phase == END) sync.tick() }
fun onChanged() = sync.markDirty()
@SubscribeEvent fun onUnload(e: WorldEvent.Unload) { if (e.world.isRemote) sync.flush() }
```

`tick()` loads when the context changes (flushing the previous one first) and writes `debounceTicks` after the first `markDirty()`.

---

## 14. Client ↔ server messages

`io.github.fopwoc.mods.framework.network` is common code: a mod writes payload codecs once and the framework carries them over each loader's custom payloads (FML event channels on GTNH, Fabric networking, NeoForge payloads).

Two rules shape it. A bad payload never disconnects anyone: a foreign protocol version, an unknown message, a truncated buffer or an oversized count are dropped and your handler is not called. And either side may lack the mod: sends to a peer without the channel are dropped, and the client can ask whether the server has it.

```kotlin
const val PROTOCOL_VERSION = 1

data class Ping(val nonce: Long, val tags: List<String>)

object PingCodec : MessageCodec<Ping> {
  override fun encode(writer: MessageWriter, payload: Ping) {
    writer.long(payload.nonce)
    writer.byte(payload.tags.size)
    payload.tags.forEach { writer.utf8(it, maxLength = 32) }
  }

  override fun decode(reader: MessageReader): Ping =
      Ping(nonce = reader.long(), tags = reader.list(max = 8, { unsignedByte() }) { utf8(32) })
}

object PingChannel : ModChannel(MOD_ID, protocolVersion = PROTOCOL_VERSION) {
  val pings = serverbound(PingCodec)   // client → server
  val pongs = clientbound(PongCodec)   // server → client
}
```

- The channel is `namespace:path` (`path` defaults to `main`); use the mod id as namespace. On GTNH the channel name is the namespace alone and must fit 20 characters.
- `MessageReader` reads are bounds-checked; `list(max, count) { … }` refuses the count before allocating, `utf8(maxLength)` caps strings, `enum<E>()` rejects unknown ordinals, `check(cond) { … }` rejects anything else. Every rejection is a `MalformedMessageException` that the channel turns into "dropped".
- Declare messages as properties of the channel object so both sides number them in the same order. Create the channel during `initialize` (NeoForge registers payloads at mod construction). Declaring is side-neutral; a dedicated server can `send` a clientbound message without ever installing its handler.
- Install handlers where the side is set up: `PingChannel.pings.handle { ping, player -> … }` in `initialize`, `PingChannel.pongs.handle { pong -> … }` in `initializeClient`. Handlers run on that side's main thread; the sender is a `GamePlayer`.
- Send with `PingChannel.pings.send(ping)` (client) and `PingChannel.pongs.send(player, pong)` (server).
- `PingChannel.isAvailableOnServer` tells a client whether the server has the channel, so an optional mod can show "not installed on this server" instead of waiting for answers that never come.

Each frame is `[message index][protocol version][payload]`, the same bytes FML's `SimpleNetworkWrapper` produced, so GTNH builds from before the common API still interoperate. Keep one message under the vanilla 32 KiB custom-payload limit; page anything larger (Hotspot's snapshot parts are the worked example).

---

## 15. Units, colours and tokens

- **`UiUnit` / `.uu`**: all sizes are integer GUI pixels (scaled by the game's GUI scale). `8.uu`, `UiUnit(8)`.
- **`UiTokens`**: `Slot = 18`, `ControlHeight = 20`, `SmallGap = 4`, `MediumGap = 6`, `PanelPadding = 8`, `StandardButtonWidth = 96`.
- **`Color`**: write colours the Android way, packed ARGB — `Color(0xFFE6E6E6)` opaque, `Color(0xA0101010)` translucent. Alpha is **not** implied: `Color(0x101010)` is fully transparent. `Color.rgb(r, g, b)` / `Color.argb(a, r, g, b)` exist for computed channels (interpolation), and `argbInt` gives the int vanilla drawing expects.
- **`Panel`**: a bordered, padded box; `PanelDefaults` holds the colours.
- **`TimeFormat`**: `millis(12.3)` → `12.30 ms` (fixed width), `millisAdaptive(0.41)` → `410 µs`.

### Theme

`MinecraftTheme` is the `MaterialTheme` of this framework: colour and text roles read from composition locals, with defaults every screen and HUD gets for free.

```kotlin
Text("Hint", style = MinecraftTheme.typography.muted)
Text("Section", style = MinecraftTheme.typography.sectionTitle)
Panel(borderColor = MinecraftTheme.colors.surfaceBorder) { … }
Text(text, style = MinecraftTheme.typography.body.copy(color = MinecraftTheme.colors.danger, wrap = true))

// Restyle a subtree:
MinecraftTheme(colors = MinecraftTheme.colors.copy(accent = Color(0xFFFF8080))) { … }
```

`ThemeColors` roles: `foreground`, `muted`, `title`, `accent`, `success`, `warning`, `danger`, `shellBackground/Border` (the menu frame), `surfaceBackground/Border` (cards), `elevatedBackground`, `chipBackground/Border`. `ThemeTypography` roles: `title`, `sectionTitle`, `body`, `muted`; there is one font in vanilla, so roles differ by colour. `Scaffold`, `Section`, `Card` and `Dialog` read the theme, so a wrapped subtree restyles them too.

---

## 16. Testing

The framework tests run without Minecraft, and so can yours:

- Pure logic (view models, formatting, geometry) — plain `kotlin.test`.
- Composition and layout — `ComposeGuiRuntime` + `ComposeRenderLayoutState` with a fake `TextMetrics` let you compose real `@Composable` content, pump frames and inspect the resulting `LayoutNode` tree (see `LayoutStateRuntimeTest` and `LazyColumnTest` in the framework).
- Input — `ComposeGuiScreenInputAdapter` with recorded `InputTarget`s (see `ComposeGuiScreenInputAdapterTest`).

LWJGL and most `net.minecraft.client` classes are not loadable in unit tests; keep them out of the code paths you want to test (pass callbacks or plain values instead).

---

## 17. How it works under the hood

- **Composition → node tree.** Composables emit `ComposeTreeNode`s through a `NodeApplier`; there is no Compose UI, so the node kinds are the framework's own (`Box`, `Column`, `Text`, `Button`, …).
- **Pump loop.** A `Recomposer` runs on a custom `MainCoroutineDispatcher` bound to the client thread. `pump()` drains dispatched tasks and snapshot notifications; the screen pumps before input and before each frame, and sends a frame clock tick per rendered frame.
- **Layout.** After composition changes (detected through a snapshot apply observer), the node tree is reduced to `LayoutShape`s and measured and placed by `LayoutEngine` into `LayoutNode`s. A structurally equivalent tree (same shapes, same children) is refreshed in place; otherwise it is laid out again. Modifier chains resolve once and are cached.
- **Drawing.** `LayoutNode.draw` walks the tree with a `RenderContext` and registers `InputTarget`s (bounds and callbacks) for hit-testing on the next click. Everything above this line is common code; the `RenderContext` is the platform's.
  - **GTNH:** `MinecraftRenderContext` wraps `FontRenderer` and `Gui.drawRect`, clips scrolling with GL scissor, and 9-slices buttons, checkboxes and sliders from `textures/gui/widgets.png`. Nothing vanilla is instantiated.
  - **26.2:** `ModernRenderContext` draws into the GUI render state (`GuiGraphicsExtractor`), maps clips to its scissor stack and blits the vanilla `widget/*` sprites, so it runs on whichever graphics backend the game uses.
- **Platform hosts.** A `ComposeScreen` is shown by the platform's screen host (`GtnhComposeScreenHost` on GTNH, `ModernComposeScreenHost` on 26.2) and HUD layers by one framework HUD element per platform. Hosts turn the platform's input into `KeyPress`es and pointer events, which is why the screen hooks look the same everywhere.

---

## Differences from Android Compose

| Android | Here |
| --- | --- |
| `dp`, `sp` | `uu` (GUI pixels). No density; the game's GUI scale applies. |
| Pointer input, gestures, drag | Only `clickable` (left click) and `hoverBackground`; no drag or multi-touch on arbitrary elements. |
| `LazyColumn` | Same idea; heights are measured as items are composed (no subcomposition), so scroll extent is an estimate until everything has been seen once. |
| `SubcomposeLayout`, `Layout {}` custom layouts | Not available. |
| `Canvas`, `drawBehind`, shapes, clipping shapes | Only rectangles: `background`, `border`, scroll clipping. |
| `Text` with `AnnotatedString`, fonts, sizes | One vanilla font, one size; styling through `StyledText` (`§` codes). |
| `animate*AsState`, transitions | Not provided (you can drive values from `withFrameNanos`). |
| `rememberSaveable` at the root | Acts as `remember`; meaningful only inside `NavHost` entries. |
| Multi-touch, focus traversal with Tab | Mouse + keyboard only; Tab is the player list. |

Compose state, effects, `key` and composition locals use the real Compose Runtime. ViewModels and navigation use the real AndroidX libraries through KNH's screen and rendering integration.

---

## Cookbook

**Open a screen from a command**: `Screens.open(MyScreen())`. It already defers to the next client tick, so it's safe from commands, key handlers and network handlers.

**Refresh a screen from game state every tick** (poll instead of observing, since Minecraft state is not Compose state): extend `ComposeMenuScreen` and key on its `refreshToken`, or count ticks yourself:

```kotlin
class MyScreen : ComposeScreen() {
  private var tick by mutableIntStateOf(0)
  override fun onTick() { tick++ }
  @Composable override fun Content() {
    val model = remember(tick) { readGameState() }
    MyView(model)
  }
}
```

**A dialog inside a screen**:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
  MainContent()
  if (confirming) {
    Box(modifier = Modifier.matchParentSize().background(Color(0x80000000)))
    Panel(modifier = Modifier.width(180.uu).align(Alignment.Center)) {
      Column(verticalArrangement = VerticalArrangement.spacedBy(6.uu)) {
        Text("Delete 3 items?", style = TextStyle(wrap = true), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = HorizontalArrangement.spacedBy(4.uu)) {
          Button(text = "Cancel", modifier = Modifier.weight(1f)) { confirming = false }
          Button(text = "Delete", modifier = Modifier.weight(1f)) { delete(); confirming = false }
        }
      }
    }
  }
}
```

Elements later in a `Box` draw on top and receive clicks first, so the dimmer swallows nothing while the panel's buttons still work.

**A clickable row** (list-item style without a vanilla button):

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(3.uu)
        .hoverBackground(Color(0x40FFFFFF))
        .clickable { onOpen(entry) },
) {
  Text(entry.title, modifier = Modifier.weight(1f))
  Text("›")
}
```

**Key chip / badge** (as used by Measure):

```kotlin
@Composable
fun KeyChip(keys: String) =
    Box(modifier = Modifier.background(Color(0xCC2A2D34)).border(Color(0xFF5A5E68)).padding(horizontal = 4.uu, vertical = 2.uu)) {
      Text(keys, style = TextStyle(color = MinecraftColor.Yellow.color))
    }
```

**Reading settings in composition**: `ModConfig` values are plain properties, not snapshot state. Read them into a `remember(config.revision)` block or a view model refresh so the UI updates after a config save.
