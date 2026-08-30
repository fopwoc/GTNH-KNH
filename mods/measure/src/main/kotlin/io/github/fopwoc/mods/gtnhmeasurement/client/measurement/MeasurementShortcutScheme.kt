package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import org.lwjgl.input.Keyboard

object MeasurementShortcutScheme {
  val platformProfile: MeasurementPlatformProfile =
      when {
        System.getProperty("os.name")?.lowercase()?.contains("mac") == true ->
            MeasurementPlatformProfile.MAC
        else -> MeasurementPlatformProfile.STANDARD
      }

  fun selectionModifierLabel(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) "⇧" else "Shift"

  fun constraintModifierLabel(): String = selectionModifierLabel()

  fun targetModifierLabel(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) "⌃" else "Ctrl"

  fun transformModifierLabel(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) {
        "⌥"
      } else {
        "Alt"
      }

  fun editorModifierLabel(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) {
        "⌘"
      } else {
        "Ctrl"
      }

  fun createClickLabel(): String = "MMB"

  fun targetedCreateClickLabel(): String = chord(targetModifierLabel(), createClickLabel())

  fun selectionClickLabel(): String = chord(selectionModifierLabel(), createClickLabel())

  fun multiSelectionClickLabel(): String =
      chord(selectionModifierLabel(), targetModifierLabel(), createClickLabel())

  fun transformClickLabel(): String = chord(transformModifierLabel(), createClickLabel())

  fun deleteLabel(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) "⌫" else "Del/Backspace"

  fun cancelLabel(): String = if (platformProfile == MeasurementPlatformProfile.MAC) "⎋" else "Esc"

  fun undoLabel(): String = chord(editorModifierLabel(), "Z")

  fun redoLabel(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) {
        chord(selectionModifierLabel(), editorModifierLabel(), "Z")
      } else {
        "${chord(editorModifierLabel(), "Y")} / ${chord(editorModifierLabel(), selectionModifierLabel(), "Z")}"
      }

  fun historySummary(): String = "${undoLabel()} undo · ${redoLabel()} redo"

  fun editClipboardSummary(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) {
        "${editorModifierLabel()}C/X/V clipboard"
      } else {
        "${editorModifierLabel()}+C/X/V clipboard"
      }

  fun selectionModifierDown(): Boolean =
      Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)

  fun targetModifierDown(): Boolean =
      Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)

  fun transformModifierDown(): Boolean =
      Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)

  fun editorModifierDown(): Boolean =
      if (platformProfile == MeasurementPlatformProfile.MAC) {
        Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)
      } else {
        Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
      }

  fun currentKeyboardSnapshot(keyPressed: (Int) -> Boolean): MeasurementInputSnapshot {
    val editorModifierDown = editorModifierDown()
    val selectionModifierDown = selectionModifierDown()
    return MeasurementInputSnapshot(
        selectionModifierDown = selectionModifierDown,
        targetModifierDown = targetModifierDown(),
        transformModifierDown = transformModifierDown(),
        editorModifierDown = editorModifierDown,
        escapeTriggered = keyPressed(Keyboard.KEY_ESCAPE),
        redoPrimaryTriggered =
            when (platformProfile) {
              MeasurementPlatformProfile.MAC ->
                  editorModifierDown && selectionModifierDown && keyPressed(Keyboard.KEY_Z)
              MeasurementPlatformProfile.STANDARD ->
                  editorModifierDown && keyPressed(Keyboard.KEY_Y)
            },
        redoSecondaryTriggered =
            platformProfile == MeasurementPlatformProfile.STANDARD &&
                editorModifierDown &&
                selectionModifierDown &&
                keyPressed(Keyboard.KEY_Z),
        undoTriggered = editorModifierDown && keyPressed(Keyboard.KEY_Z),
        copyTriggered = editorModifierDown && keyPressed(Keyboard.KEY_C),
        cutTriggered = editorModifierDown && keyPressed(Keyboard.KEY_X),
        pasteTriggered = editorModifierDown && keyPressed(Keyboard.KEY_V),
        deleteTriggered = keyPressed(Keyboard.KEY_DELETE) || keyPressed(Keyboard.KEY_BACK),
    )
  }

  fun currentWorldClickSnapshot(): MeasurementInputSnapshot =
      MeasurementInputSnapshot(
          selectionModifierDown = selectionModifierDown(),
          targetModifierDown = targetModifierDown(),
          transformModifierDown = transformModifierDown(),
          editorModifierDown = editorModifierDown(),
      )

  fun redoPrimaryPressed(keyPressed: (Int) -> Boolean): Boolean =
      when {
        platformProfile == MeasurementPlatformProfile.MAC ->
            editorModifierDown() && selectionModifierDown() && keyPressed(Keyboard.KEY_Z)
        else -> editorModifierDown() && keyPressed(Keyboard.KEY_Y)
      }

  fun redoSecondaryPressed(keyPressed: (Int) -> Boolean): Boolean =
      platformProfile == MeasurementPlatformProfile.STANDARD &&
          editorModifierDown() &&
          selectionModifierDown() &&
          keyPressed(Keyboard.KEY_Z)

  fun footerText(): String =
      if (platformProfile == MeasurementPlatformProfile.MAC) {
        "${createClickLabel()} create · ${targetModifierLabel()} offset · ${selectionModifierLabel()} select · ${transformModifierLabel()} move/resize · ${editorModifierLabel()}C/X/V · ${undoLabel()} · ${redoLabel()} · ${cancelLabel()} cancel"
      } else {
        "${createClickLabel()} create · ${targetModifierLabel()} offset · ${selectionModifierLabel()} select · ${transformModifierLabel()} move/resize · ${editorModifierLabel()}+C/X/V · ${undoLabel()} · ${redoLabel()} · ${cancelLabel()} cancel"
      }

  private fun chord(vararg keys: String): String =
      when (platformProfile) {
        MeasurementPlatformProfile.MAC -> {
          val mouseKey = keys.lastOrNull()?.takeIf { it == createClickLabel() }
          if (mouseKey != null) {
            keys.dropLast(1).joinToString(separator = "") + " " + mouseKey
          } else {
            keys.joinToString(separator = "")
          }
        }
        MeasurementPlatformProfile.STANDARD -> keys.joinToString(separator = "+")
      }
}
