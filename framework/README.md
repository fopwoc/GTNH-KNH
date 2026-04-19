# KNH Core

Shared runtime library mod for the mods in this monorepo.

## What it provides

- reusable shared Kotlin code used by the other mods
- shared JSON/config helpers
- a real Compose Runtime-backed declarative UI layer for simple GTNH client screens
- bundled `kotlinx.serialization` runtime classes
- a separate Forge/FML mod jar that other mods can depend on at runtime, similar to `Forgelin`

## Compose Runtime GUI proof of concept

The framework now includes a real Compose Runtime integration under
`io.github.fopwoc.mods.framework.ui.compose`.

The implementation is now split by layer/package instead of a few large files:

- `ui.compose.foundation` — foundation-style layout/text primitives like `Box`, `Column`, `Row`, `Spacer`, `Text`
- `ui.compose.component.native` — direct native Minecraft/Forge widget bindings with Compose-first names like `Button`, `Checkbox`, `TextField`, `Slider`
- `ui.compose.component` — composed helpers built from those native bindings like `Panel`, `Tabs`, `ToggleButton`
- `ui.compose.model` — alignment, modifiers, styles, and immutable UI element models
- `ui.compose.state` / `ui.compose.runtime` — state holders and `remember...` helpers
- `ui.compose.node` — Compose runtime node/applier bridge
- `ui.compose.layout` — layout, geometry, rendering contracts, and scroll behavior
- `ui.compose.minecraft` — the `GuiScreen` host and Minecraft-specific background/render integration

Current proof-of-concept features:

- actual `@Composable` functions compiled by the Kotlin Compose compiler plugin
- runtime state via `remember { ... }` and `mutableStateOf(...)`
- a custom Compose `Applier` that builds a Minecraft-oriented UI tree
- declarative foundation primitives `Box`, `Column`, `Row`, `Text`, and `Spacer`
- direct native bindings `Button`, `Checkbox`, `TextField`, `Slider`, and `SelectableList`
- reusable composite helpers `Panel`, `ToggleButton`, `Tabs`, and `SegmentedControl`
- hosted real vanilla `GuiTextField` inputs via declarative `TextField` composables
- hosted native Forge `GuiCheckBox` controls via declarative `Checkbox` composables
- hosted native Forge `GuiSlider` controls via declarative `Slider` composables
- hosted native Minecraft `GuiSlot` selectable lists via `SelectableList`
- wrapped multiline text rendering for constrained-width labels and paragraphs
- scrollable rows/columns via `Modifier.verticalScroll(rememberScrollState())` and `Modifier.horizontalScroll(...)`, with wheel scrolling and draggable scrollbar thumbs
- cached layout-element conversion and relayout between snapshot invalidations and viewport changes
- unified render-order input target dispatch for hosted widgets, scroll wheels, scrollbar thumbs, and text-field focus
- a Minecraft `GuiScreen` host in `ui.compose.minecraft.ComposeGuiScreen`
- real AndroidX `ViewModel` screen scope via `androidx.lifecycle` + `androidx.lifecycle.viewmodel.compose.viewModel()`

`ComposeGuiScreen` now exposes a first-class background style hook via
`composeBackgroundStyle`:

- `ComposeBackgroundStyle.Color(...)` draws a full-screen ARGB color fill
- `ComposeBackgroundStyle.VanillaDefault` uses Minecraft's standard `drawDefaultBackground()` helper
- `ComposeBackgroundStyle.None` skips the automatic background fill entirely

The default style remains a semi-transparent overlay:

```kotlin
override val composeBackgroundStyle = ComposeBackgroundStyle.Color(Color(0xA0101010))
```

`Color(...)` uses strict ARGB semantics, so pass an explicit alpha channel there.
Use `Color(0xFF101010)` for an opaque packed color, or `Color.rgb(...)` when you
want the Android-style explicit opaque RGB helper.

For named vanilla UI colors, use `MinecraftColor.<name>.color`, which mirrors
the standard Minecraft chat/formatting palette as `Color` values:

```kotlin
Text(
	text = "Warning",
	style = TextStyle(color = MinecraftColor.Gold.color)
)
```

Example:

```kotlin
class MyScreen : ComposeGuiScreen() {
	override val composeBackgroundStyle = ComposeBackgroundStyle.VanillaDefault

	@Composable
	override fun Content() {
		// ...
	}
}
```

If you need something more custom than those built-in options, you can still override
`drawComposeBackground()` directly.

Scroll layout intentionally keeps measurement pure while applying the current
`ScrollState` during placement/render targeting. That split keeps `LayoutEngine`
deterministic for a given element tree while still letting scrollbars, wheel
targets, and viewport offsets reflect the latest mutable state each frame.

The rendering backend is still native Minecraft drawing and input handling, but the
screen authoring model now uses the real Compose runtime/recomposer pipeline.

## AndroidX ViewModel support

`ComposeGuiScreen` now provides a real AndroidX `ViewModelStoreOwner` to the composition,
so composables inside a screen can use the normal Jetpack API:

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MyViewModel : ViewModel() {
	var clicks by mutableStateOf(0)
}

@Composable
override fun Content() {
	val vm: MyViewModel = viewModel()
	// use vm like normal
}
```

Scope rules:

- one `ViewModelStore` per `ComposeGuiScreen` instance
- recomposition and `initGui()`/resize do **not** recreate the `ViewModel`
- `onGuiClosed()` clears the store and triggers `ViewModel.onCleared()`

Implementation note: this project uses the JetBrains-published AndroidX lifecycle
artifacts that match `org.jetbrains.compose.runtime`, and intentionally excludes the
heavy Compose UI desktop runtime because this Minecraft host only needs runtime-level
ViewModel integration.

`Button` now hosts Forge's native `GuiButtonExt`, `Checkbox` hosts Forge's
native `GuiCheckBox`, `Slider` hosts Forge's `GuiSlider`, and `SelectableList`
hosts Minecraft's native `GuiSlot`, while state and event
dispatch still flow through the Compose-backed wrapper.

There is also a tiny demo mod in `mods/testgui` that opens a showcase screen with
`/testgui`.

## Runtime role

This jar is intended to be shipped in the modpack alongside:

- `Forgelin`
- `DejaVu`
- `measure`
- `Tps tab`

The other mods compile against this framework but do not shade it into their own jars.

## Build locally

```bash
../gradlew -p . build publishToMavenLocal
```

## Expected artifact

```text
build/libs/knh-core-<version>.jar
```

