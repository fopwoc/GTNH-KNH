# Test GUI

Tiny client-only demo mod for the KNH Compose screen layer.

## What it does

- registers the `/testgui` client command
- opens a friendly multi-tab `ComposeGuiScreen` sample that now lands on a dedicated hosted-controls tab
- shows a mix of small postcard-style scenes, placeholder copy, toggles, buttons,
  text fields, sliders, segmented controls, a native selectable list, and a custom
  scrolling column with draggable scrollbar
- includes a live hosted widget smoke test for the native button, checkbox, text field,
  slider, and selectable list bindings
- acts as a richer reference screen for composing simple in-game menus and mockups

## Local build

```bash
../../gradlew -p . build
```

