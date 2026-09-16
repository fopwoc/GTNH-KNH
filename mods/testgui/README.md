# Test GUI

Storybook for [KNH Core](../../framework/): every component and behaviour of the framework, each in its meaningful states, in one screen. For framework development; not for packs.

![testgui1.png](https://raw.githubusercontent.com/fopwoc/GTNH-KNH/main/.github/assets/testgui1.png)

`/testgui` opens the gallery: stories on the left, the selected one on the right. `/testgui hud` toggles the HUD overlay demo.

## Stories

Button · Checkbox · Slider · TextField · SelectableList · LazyColumn · Scroll · Tabs · SegmentedControl · ToggleButton · Surfaces (Panel, Card, Section, Dialog) · Text & tooltips · Theme · Layout · Modifiers · State & ViewModel · Navigation · HUD overlay · GPU canvas · Stress: dense widgets · Stress: scroll & clip

The **GPU canvas** story shows a scaled whole image, overlapping translucent images, and a Compose-owned grid controller that selects generated RGBA images and LOD. Use the arrow buttons to pan, `−`/`+` to zoom, and Reset to recenter. The canvases should remain clipped to their bounds, with no stale or duplicated cells while moving.

## Adding a story

One file under `client/gui/ui/story/`, one composable, states wrapped in `Example("label") { … }`:

```kotlin
@Composable
fun MyWidgetStory() {
  Examples {
    Example("Default") { MyWidget() }
    Example("Disabled") { MyWidget(enabled = false) }
  }
}
```

Then add `Story("MyWidget") { MyWidgetStory() }` to `StoryCatalog`. Stories may `remember` their own state; the gallery keeps a scroll position per story and a ViewModel store per screen.

## Build

```bash
./gradlew -p framework publishToMavenLocal
./gradlew -p mods/testgui clean build
```
