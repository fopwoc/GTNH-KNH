package io.github.fopwoc.mods.framework.ui.compose.layout.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers

internal interface TextMetrics {
  val lineHeight: Int

  fun textWidth(text: String): Int

  fun wrapText(text: String, maxWidth: Int): List<String>

  /** Longest prefix (or suffix when [fromEnd]) of [text] that fits into [maxWidth]. */
  fun trimToWidth(text: String, maxWidth: Int, fromEnd: Boolean = false): String {
    var width = 0
    val builder = StringBuilder()
    val indices = if (fromEnd) text.indices.reversed() else text.indices
    for (index in indices) {
      val char = text[index]
      val charWidth = textWidth(char.toString())
      if (width + charWidth > maxWidth) {
        break
      }
      width += charWidth
      if (fromEnd) builder.insert(0, char) else builder.append(char)
    }
    return builder.toString()
  }
}

/** Focus bookkeeping a text field needs from its host; HUD overlays use [None]. */
internal interface TextFieldHost {
  fun rendered(state: TextFieldState, maxLength: Int)

  fun focus(state: TextFieldState)

  object None : TextFieldHost {
    override fun rendered(state: TextFieldState, maxLength: Int) = Unit

    override fun focus(state: TextFieldState) = Unit
  }
}

internal interface RenderContext : TextMetrics {
  val viewportWidth: Int
  val viewportHeight: Int
  val mouseX: Int
  val mouseY: Int

  fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color)

  fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color)

  fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color)

  fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean)

  fun registerInputTarget(target: InputTarget)

  fun withClipRect(rect: Rect, block: () -> Unit)

  val textFields: TextFieldHost
    get() = TextFieldHost.None

  /** Modifier keys held right now; read inside input callbacks. */
  fun keyModifiers(): KeyModifiers = KeyModifiers.None
}
