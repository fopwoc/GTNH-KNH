package io.github.fopwoc.mods.framework.ui.compose.layout.render

/** A 9-slice source rectangle on `textures/gui/widgets.png` with its stretch borders. */
internal data class WidgetSlice(
    val u: Int,
    val v: Int,
    val width: Int,
    val height: Int,
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
)

internal object WidgetSprites {
  val ButtonDisabled =
      WidgetSlice(u = 0, v = 46, width = 200, height = 20, top = 2, bottom = 3, left = 2, right = 2)
  val ButtonNormal = ButtonDisabled.copy(v = 66)
  val ButtonHovered = ButtonDisabled.copy(v = 86)
  const val SLIDER_KNOB_WIDTH = 8
  const val SLIDER_KNOB_HEIGHT = 20
}
