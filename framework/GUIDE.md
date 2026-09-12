# KNH Core developer guide

KNH Core lets you write Minecraft 1.7.10 GUIs and HUD overlays with real Jetpack Compose: the Compose *runtime* (composition, state, effects, `remember`, coroutines) drives a small layout engine that draws with vanilla `FontRenderer`/`Gui` primitives. There is no Compose UI or Skia involved — if you know Compose on Android, almost everything transfers; what differs is listed in [Differences from Android Compose](#differences-from-android-compose).

This guide covers everything the framework offers, in the order you will need it. All snippets compile against the packages under `io.github.fopwoc.mods.framework`.

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
12. [Settings with ForgeConfig](#12-settings-with-forgeconfig)
13. [Saving data as JSON](#13-saving-data-as-json)
14. [Client ↔ server messages](#14-client--server-messages)
15. [Units, colours and tokens](#15-units-colours-and-tokens)
16. [Testing](#16-testing)
17. [How it works under the hood](#17-how-it-works-under-the-hood)
18. [Differences from Android Compose](#differences-from-android-compose)
19. [Cookbook](#cookbook)

---

## 1. Setting up a mod

KNH Core is a separate Forge mod. Your mod depends on it at compile time and declares it as a runtime dependency; the Compose/lifecycle/serialization libraries are bundled inside `knh-core.jar` and must **not** be bundled again. The Kotlin standard library and coroutines come from Forgelin — coroutines are shaded inside its jar, so you compile against that copy and never declare `kotlinx-coroutines` yourself (the shared conventions exclude it from every mod classpath).

`build.gradle.kts` (the `mods/*` builds in this repository are the reference):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)   // required: it rewrites @Composable functions
    alias(libs.plugins.gtnh.convention)
    alias(libs.plugins.buildconfig)
}

dependencies {
    implementation(libs.forgelin)
    implementation("io.github.fopwoc.mods:knh-core:<version>") { isTransitive = false }
    // Compile against the libraries knh-core ships, without packaging them:
    compileOnly(libs.compose.runtime)
    compileOnly(libs.compose.runtime.saveable)
    compileOnly(libs.lifecycle.runtime.compose)
    compileOnly(libs.lifecycle.viewmodel)
    compileOnly(libs.lifecycle.viewmodel.compose) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    compileOnly(libs.serialization.json)   // only if you use JsonFileStorage
}
```

The mod class follows the usual Forge shape; `ModProxy` is a tiny base class with `preInit(configDirectory)` and `init()` hooks:

```kotlin
@Mod(
    modid = MOD_ID, name = MOD_NAME, version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;",
    acceptableRemoteVersions = "*",          // client-side mod: any server is fine
)
object MyMod {
  @SidedProxy(clientSide = CLIENT_PROXY_CLASS, serverSide = SERVER_PROXY_CLASS)
  lateinit var proxy: ModProxy

  @Mod.EventHandler fun onPreInit(event: FMLPreInitializationEvent) = proxy.preInit(event.modConfigurationDirectory)
  @Mod.EventHandler fun onInit(event: FMLInitializationEvent) = proxy.init()
}

class ClientProxy : ModProxy() {
  override fun init() {
    ClientCommandHandler.instance.registerCommand(OpenMenuCommand)
  }
}
class ServerProxy : ModProxy()
```

Everything that touches `net.minecraft.client` must live in client-only classes (`@SideOnly(Side.CLIENT)`) or be reached only from the client proxy, exactly as in any Forge mod. The framework's screen and overlay classes are already `@SideOnly(Side.CLIENT)`.

---

## 2. Your first screen

Extend `ComposeGuiScreen` and implement `Content()`:

```kotlin
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen

@SideOnly(Side.CLIENT)
class HelloScreen : ComposeGuiScreen() {
  override val composeBackgroundStyle = ComposeBackgroundStyle.VanillaDefault

  @Composable
  override fun Content() {
    var clicks by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
      Panel(modifier = Modifier.width(220.uu).align(Alignment.Center)) {
        Column(verticalArrangement = VerticalArrangement.spacedBy(6.uu)) {
          Text("Hello from Compose Runtime")
          Text("Clicked $clicks times", style = TextStyle(color = MinecraftColor.Gray.color))
          Button(text = "Click me", modifier = Modifier.fillMaxWidth()) { clicks++ }
          Button(text = "Close", modifier = Modifier.fillMaxWidth()) { mc.displayGuiScreen(null) }
        }
      }
    }
  }
}
```

Open it like any `GuiScreen`: `Minecraft.getMinecraft().displayGuiScreen(HelloScreen())`. From a command, key binding or network handler use `ScreenOpener.open(::HelloScreen)` — it defers to the next client tick and only opens while the player is in a world.

### A mod menu in three lines each

Most mod screens are the same shape: no pause, no dimmed background, close on the key that opened them, and re-read some runtime state every tick. That is `ComposeMenuScreen`; wiring the key binding and the chat command is `ClientKeyBindings` / `ClientCommand`:

```kotlin
class MyMenuScreen : ComposeMenuScreen(toggleKey = MyKeys.openMenu) {
  @Composable
  override fun Content() =
      MyMenuRoute(width, height, refreshToken = refreshToken, onClose = ::requestClose)
}

object MyKeys {
  lateinit var openMenu: KeyBinding
  fun register() {
    openMenu = ClientKeyBindings.bind("key.mymod.openMenu", "key.categories.mymod") {
      ScreenOpener.open(::MyMenuScreen)
    }
  }
}

object MyCommand : ClientCommand(name = "mymod", usage = "/mymod | /mymod reset") {
  override fun run(args: List<String>): String? = when (args.firstOrNull()) {
    null -> { ScreenOpener.open(::MyMenuScreen); null }   // null = no chat reply
    "reset" -> { MyState.reset(); "Reset" }
    else -> usage
  }
  override fun complete(args: List<String>) = if (args.size == 1) listOf("reset") else emptyList()
}
```

`refreshToken` increments every tick; key a `LaunchedEffect(refreshToken)` on it in the route to poll non-Compose state. Override `onUnhandledKey` for extra shortcuts and call `super` first so the toggle key keeps working; `refreshNow()` re-reads before the next tick.

For the look, `Scaffold` (centred panel, title/subtitle, Close), `Section` (titled card), `Card` and `Dialog` (a small message with one button, for "cannot open" cases) are plain components in `ui.compose.component` that read [`MinecraftTheme`](#theme) — like Material's `Scaffold`/`Card` read `MaterialTheme`. Don't want the theme? Build your own from `Panel`, `Column` and `Text`.

What `ComposeGuiScreen` does for you:

- owns one Compose runtime and a `ViewModelStore`; `initGui()` on resize **reuses** the composition, `onGuiClosed()` disposes it and clears view models,
- pumps the runtime before every input event and every frame, so state written by a click is visible in the same frame,
- routes mouse/keyboard input to the topmost element under the cursor,
- draws tooltips for anything with `Modifier.tooltip`,
- turns Escape into a back event (see [Input, focus and the back key](#11-input-focus-and-the-back-key)) before falling back to vanilla close.

Background styles: `ComposeBackgroundStyle.VanillaDefault` (the dimmed vanilla background), `ComposeBackgroundStyle.None`, or `ComposeBackgroundStyle.Color(Color(0xA0101010))`.

Overridable hooks: `drawComposeBackground()`, `drawComposeFallback()` (called after the Compose tree; the default draws vanilla `GuiScreen` buttons, so you can mix in legacy widgets), `doesGuiPauseGame()`, and `onUnhandledKey(typedChar, keyCode)` for screen-wide shortcuts — it runs only for keys no text field, `BackHandler` or `NavHost` consumed, and returning `true` swallows the key before vanilla's Escape-closes-screen handling.

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

All controls are drawn by the framework. Buttons, checkboxes and sliders use the vanilla `widgets.png` sheet and the vanilla click sound, so they look and feel exactly like the rest of the game while behaving as ordinary layout elements (any size, any modifier, no widget instances to keep in sync).

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

`ComposeGuiScreen` is a `ViewModelStoreOwner`; the AndroidX `viewModel()` API is available:

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

Use the no-argument `viewModel(Class)` form with a no-arg constructor (a `NewInstanceFactory` is installed), or `viewModel { SettingsViewModel(dependency) }` with a factory lambda. View models survive `initGui()` re-runs (window resize) and are cleared when the screen closes. Inside a `NavHost`, every entry gets its own owner, cleared when the entry leaves the back stack.

`collectAsStateWithLifecycle` is the framework's own (`ui.compose.runtime`); it stops collecting while the owner is below `STARTED`, which happens for covered navigation entries.

### Coroutines

`LaunchedEffect`, `rememberCoroutineScope()` and `viewModelScope` all dispatch on the client thread through the runtime's main dispatcher; `withFrameNanos` resolves once per rendered frame. Long work belongs on `Dispatchers.IO`/`Default` — switch back with `withContext(Dispatchers.Main)` before touching Minecraft. Exceptions in effects or the recomposer are logged and rethrown on the next frame so they show up as a normal crash report instead of a frozen screen.

### Screen-scoped state

There is no `SaveableStateRegistry` at the screen root, so `rememberSaveable` behaves like `remember` there. It does matter inside `NavHost` entries declared with `retainSaveableState = true`: a scroll position or text draft survives navigating away and back.

---

## 9. Navigation

A typed back stack with one composable per key, modelled after Navigation 3. Why not the real Navigation 3? It ships Java 11 bytecode and GTNH runs on Java 8, so it cannot be loaded at all — the same reason the Compose runtime and lifecycle libraries are pinned to their last Java-8-compatible releases. The API here keeps the same shape (`NavKey`, a back stack, `entryProvider { entry<Key> { } }`, `NavHost`) so the knowledge transfers, but it is a small independent implementation.

```kotlin
sealed interface Dest : NavKey {
  data object Home : Dest
  data class Details(val id: Long) : Dest
}

@Composable
fun App() {
  val backStack = rememberNavBackStack(Dest.Home)          // or rememberNavBackStack(Home, keySaver = mySaver)
  val navigator = rememberNavigator(backStack)

  NavHost(
      backStack = backStack,
      entryProvider = entryProvider {
        entry<Dest.Home>(retainSaveableState = true) {
          HomeRoute(onOpen = { navigator.push(Dest.Details(it)) })
        }
        entry<Dest.Details> { key ->
          DetailsRoute(id = key.id, onBack = { navigator.pop() })
        }
      },
      handleBack = true,                                   // Escape pops while canPop
      emptyContent = { Text("nothing here") },
  )
}
```

`Navigator`: `push/navigate`, `pop/navigateBack`, `replaceTop`, `popToRoot`, `clear`, `currentKey`, `entries`, `canPop`. Each entry has its own `ViewModelStore` and lifecycle (`RESUMED` on top, `STARTED` when covered, destroyed when removed). Inside an entry lambda `this` is a `NavEntryScope` with the same navigation methods and the entry's `key`/`entryId`.

Keys must be usable as stable identities; data objects and data classes are ideal. Provide a `keySaver` only if you want the stack to survive `retainSaveableState` round-trips in a nested host.

---

## 10. HUD overlays

`ComposeHudOverlay` renders a composition during `RenderGameOverlayEvent` (or any render callback) without a screen. The recipe used by TPS Tab and Measure:

```kotlin
@SideOnly(Side.CLIENT)
object MyHud {
  private val host = ComposeHudOverlay { Content(state) }
  private var state by mutableStateOf(HudModel())

  @SubscribeEvent
  fun onRender(event: RenderGameOverlayEvent.Post) {
    if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return
    val mc = Minecraft.getMinecraft()
    if (!shouldShow()) { host.dispose(); return }

    state = computeModel()                                 // cheap: same value → no recomposition
    host.render(client = mc, font = mc.fontRenderer, width = event.resolution.scaledWidth, height = event.resolution.scaledHeight)
  }

  @Composable
  private fun Content(model: HudModel) {
    Box(modifier = Modifier.fillMaxSize()) {
      HudAnchor(bounds = HudRect(left = 4, top = 4, width = 120, height = 40), contentAlignment = Alignment.TopStart) {
        Column(modifier = Modifier.background(Color(0x80000000)).padding(4.uu)) {
          Text(model.title)
        }
      }
    }
  }
}
```

- `render()` creates the composition on first use; `dispose()` tears it down and is a no-op when nothing is running — calling it every hidden frame is fine.
- `HudAnchor(bounds, contentAlignment)` positions a box at screen coordinates; children align inside it.
- Overlays receive no keyboard or mouse input; they are display-only.
- A HUD overlay and a `ComposeGuiScreen` can coexist (Tab held while a menu is open).
- **Threading**: FML network events (`ClientDisconnectionFromServerEvent`, channel registration) arrive on Netty threads. Never touch Compose state or `dispose()` from them — set a flag and act on the next client tick.

---

### Drawing in the world

Boxes, spheres, lines and labels at world positions — Measure's shapes, Hotspot's chunk columns — go through `WorldOverlay` from `RenderWorldLastEvent`. It sets the overlay GL state (no texture, lighting, depth or culling; blending on), gives you world coordinates (the camera offset is applied for you, and follows the render view entity so freecam works), and draws labels after all shapes:

```kotlin
@SubscribeEvent
fun onRenderWorld(event: RenderWorldLastEvent) =
    WorldOverlay.render(event.partialTicks) {
      glassBox(x, y, z, x + 3.0, y + 2.0, z + 3.0, color)          // translucent, glowing rims
      glassSphere(cx, cy, cz, radius, color)
      blockOutline(bx, by, bz, color, width = 2f)                    // wire, for single blocks
      line(x1, y1, z1, x2, y2, z2, color, width = 2f)
      label(x + 1.5, y + 2.5, z + 1.5, listOf("3 × 2 × 3", "detail"), color)
      camera.eyeX                                                     // where the viewer looks from
    }
```

Glass surfaces are drawn without a depth test so the whole shape is visible through terrain and from inside; the rim alpha is what outlines them. Labels are billboards drawn at full brightness (the font goes through the lightmap and would be black inside blocks otherwise).

---

## 11. Input, focus and the back key

- Clicks are dispatched to the topmost input target under the cursor: buttons, checkboxes, sliders, lists, text fields, scroll thumbs. Drags continue to the element that captured the press (sliders, scrollbars, text selection).
- The mouse wheel goes to the innermost scrollable under the cursor.
- Keyboard input goes to the focused `TextField`; otherwise Escape is offered to `BackHandler`s (innermost first), then to `NavHost`, and finally closes the screen.

```kotlin
BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }
```

`BackHandlerResult { consumed }` is the variant whose callback decides whether the event was handled. Handlers nest: the innermost enabled one runs first, then `NavHost(handleBack = true)`, then the screen closes.

Modifier keys during a click are available to the framework's own controls (multi-select). Custom in-world input (like Measure's middle-click) is ordinary Forge `MouseEvent`/`KeyInputEvent` handling and unrelated to the GUI layer.

---

## 12. Settings with ForgeConfig

`ForgeConfig` wraps a single-category Forge `.cfg` file with typed, normalized values and gives you the vanilla **Mods → Config** screen for free.

```kotlin
object MyConfig : ForgeConfig(modId = MOD_ID, fileName = "my_mod.cfg") {
  val enabled by boolean("enabled", default = true, comment = "Master switch.")
  val interval by int("interval", default = 20, min = 1, max = 1200, comment = "Ticks between updates.")
  val stale by int("stale", default = 60, min = 1, comment = "…", normalize = { it.coerceAtLeast(interval * 2) })
  val scale by double("scale", default = 1.0, min = 0.5, max = 2.0, comment = "…")
  val alignment by enum("alignment", default = Side.LEFT, comment = "…")           // stored lower-case
  val ids by string("ids", default = "", comment = "…", normalize = { it.trim() })

  override fun onLoaded() { /* derive caches from the normalized values */ }
}
```

Wiring:

```kotlin
// ClientProxy
override fun preInit(configDirectory: File) = MyConfig.load(configDirectory)
override fun init() { FMLCommonHandler.instance().bus().register(MyConfig) }   // picks up in-game edits

// GUI factory named in @Mod(guiFactory = "…MyGuiFactory")
@SideOnly(Side.CLIENT)
class MyGuiFactory : ConfigGuiFactory() {
  override fun screenClass() = MyConfigScreen::class.java
}
@SideOnly(Side.CLIENT)
class MyConfigScreen(parent: GuiScreen) : ConfigScreen(parent, MyConfig, "My Mod configuration")
```

Behaviour: values are read, normalized **in declaration order** (a normalizer may read settings declared above it), written back, and saved if anything changed. `revision` increments per load so caches can key on it. Poll `refreshIfChanged()` (once a second is plenty) to notice edits made to the file while the game runs. Language keys default to `config.<modid>.<key>` with `.tooltip` for hover text; add them to your `.lang` file for nicer labels.

---

## 13. Saving data as JSON

For mod *state* (not settings) use kotlinx.serialization through `JsonFileStorage`:

```kotlin
@Serializable data class Bookmarks(val version: Int = 1, val entries: List<Bookmark> = emptyList())

val file = JsonFileStorage.modConfigFile(Minecraft.getMinecraft().mcDataDir, MOD_ID, "bookmarks", "$contextId.json")
val loaded = JsonFileStorage.readOrDefault(file, defaultValue = ::Bookmarks) { logger.warn("bad file", it) }
JsonFileStorage.write(file, loaded.copy(entries = entries))
```

`FrameworkJson.prettyConfig` is the shared `Json` (pretty, ignores unknown keys, encodes defaults). Writes are synchronous — debounce them from a tick handler if they can happen many times per second, and flush on `WorldEvent.Unload`.

### Per world or server

State that belongs to the world the player is in (saved measurements, the last profile) goes through `WorldScopedJsonStore` + `WorldScopedSync`; the key is `ClientWorldContext.currentId()` — `singleplayer-<world>` or `server-<address>`:

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

`io.github.fopwoc.mods.framework.network` wraps FML's `SimpleNetworkWrapper` so a mod only writes the payload codec.

The rule that shapes it: in 1.7.10 an exception escaping `IMessage.fromBytes` makes FML **kick the connection**. So a `VersionedMessage` never throws — a foreign protocol version, a truncated buffer or an oversized count leave `payload == null`, and the channel simply does not call your handler.

```kotlin
const val PROTOCOL_VERSION = 1

data class Ping(val nonce: Long, val tags: List<String>)

class PingMessage() : VersionedMessage<Ping>(PROTOCOL_VERSION) {
  constructor(ping: Ping) : this() { payload = ping }

  override fun encode(buffer: ByteBuf, payload: Ping) {
    buffer.writeLong(payload.nonce)
    buffer.writeByte(payload.tags.size)
    payload.tags.forEach { buffer.writeUtf8(it, maxLength = 32) }
  }

  override fun decode(reader: MessageReader): Ping =
      Ping(nonce = reader.long(), tags = reader.list(max = 8, { unsignedByte() }) { utf8(32) })
}

object PingChannel : ModChannel("mymod") {
  val pings = serverbound(PingMessage::class.java)   // client → server
  val pongs = clientbound(PongMessage::class.java)   // server → client
}
```

- `MessageReader` reads are bounds-checked; `list(max, count) { … }` refuses the count before allocating, `utf8(maxLength)` caps strings, `enum<E>()` rejects unknown ordinals, `check(cond) { … }` rejects anything else. Every rejection is a `MalformedMessageException` that the base class turns into "no payload".
- Declare messages as properties of the channel object so both sides register them in the same order (discriminators are sequential). Declaring is side-neutral; a dedicated server can `send` a clientbound message without ever installing its handler.
- Install handlers from the proxy that owns the side: `PingChannel.pings.handle { ping, player -> … }` in the common proxy, `PingChannel.pongs.handle { pong -> … }` in the client proxy. Handlers run on that side's main thread.
- Send with `PingChannel.pings.send(PingMessage(ping))` (client) and `PingChannel.pongs.send(player, PongMessage(pong))` (server).
- `ClientChannelTracker.watch(channel) { onDisconnect() }` tells a client whether the server advertises the channel, so an optional mod stays silent on servers without it. The flag is safe to read from any thread; the disconnect callback runs on a Netty thread.

Keep one message under the vanilla 32 KiB custom-payload limit; page anything larger (Hotspot's snapshot parts are the worked example).

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
- **Layout.** After composition changes (detected through a snapshot apply observer), the node tree is reduced to `LayoutShape`s and measured/placed by `LayoutEngine` into `LayoutNode`s. A structurally equivalent tree (same shapes, same children) is refreshed in place; otherwise it is relaid out. Modifier chains resolve once and are cached.
- **Drawing.** `LayoutNode.draw` walks the tree with a `RenderContext` that wraps `FontRenderer`/`Gui.drawRect`, applies GL scissor for scrolling, and registers `InputTarget`s (bounds + callbacks) for hit-testing on the next click.
- **Widget sheet.** Buttons/checkboxes/sliders are 9-sliced from `textures/gui/widgets.png` through `RenderContext.drawWidgetSlice`/`drawWidgetSprite`; nothing vanilla is instantiated.

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

Everything else — state, effects, `key`, composition locals, view models, `StateFlow` collection, coroutines, navigation — is the Compose you know.

---

## Cookbook

**Open a screen from a command** (commands run on the client thread but mid-tick; defer):

```kotlin
object OpenCommand : CommandBase() {
  override fun processCommand(sender: ICommandSender, args: Array<out String>) { ScreenController.requestOpen() }
}
object ScreenController {
  private var requested = false
  fun requestOpen() { requested = true }
  @SubscribeEvent fun onTick(event: TickEvent.ClientTickEvent) {
    if (event.phase == TickEvent.Phase.END && requested) { requested = false; Minecraft.getMinecraft().displayGuiScreen(MyScreen()) }
  }
}
```

**Refresh a screen from game state every tick** (poll instead of observing, since Minecraft state is not Compose state):

```kotlin
class MyScreen : ComposeGuiScreen() {
  private var tick by mutableIntStateOf(0)
  override fun updateScreen() { super.updateScreen(); tick++ }
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

**Reading Forge settings in composition**: `ForgeConfig` values are plain properties, not snapshot state. Read them into a `remember(config.revision)` block or a view model refresh so the UI updates after a config save.
