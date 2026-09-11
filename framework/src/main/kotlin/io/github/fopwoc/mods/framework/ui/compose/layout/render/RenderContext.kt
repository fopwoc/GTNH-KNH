package io.github.fopwoc.mods.framework.ui.compose.layout.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers

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

  /** Draws a 9-sliced region of the vanilla widgets sheet (buttons, boxes). */
  fun drawWidgetSlice(slice: WidgetSlice, x: Int, y: Int, width: Int, height: Int) = Unit

  /** Draws a region of the vanilla widgets sheet at its native size. */
  fun drawWidgetSprite(u: Int, v: Int, width: Int, height: Int, x: Int, y: Int) = Unit

  fun playClickSound() = Unit

  /** Modifier keys held right now; read inside input callbacks. */
  fun keyModifiers(): KeyModifiers = KeyModifiers.None
}
