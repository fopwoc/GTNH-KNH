package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard
import net.minecraft.client.gui.GuiScreen

/** Modifier keys and clipboard as seen by a focused text field. */
internal interface KeyboardEnvironment {
  fun modifiers(): KeyModifiers

  val clipboard: TextClipboard
}

internal object LwjglKeyboardEnvironment : KeyboardEnvironment {
  override fun modifiers(): KeyModifiers =
      KeyModifiers(ctrl = GuiScreen.isCtrlKeyDown(), shift = GuiScreen.isShiftKeyDown())

  override val clipboard: TextClipboard =
      object : TextClipboard {
        override fun read(): String = GuiScreen.getClipboardString() ?: ""

        override fun write(text: String) = GuiScreen.setClipboardString(text)
      }
}
