package io.github.fopwoc.mods.framework.ui.compose.layout.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.WidgetSlice
import io.github.fopwoc.mods.framework.ui.compose.layout.render.WidgetSprites
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawContainer
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import java.util.Locale

// Vanilla control visuals drawn from the widgets sheet, matching GuiButton/GuiCheckBox/GuiSlider.

private val TEXT_NORMAL = Color(0xFFE0E0E0)
private val TEXT_HOVERED = Color(0xFFFFFFA0)
private val TEXT_DISABLED = Color(0xFFA0A0A0)
private const val CHECKBOX_BOX = 11

private fun buttonSlice(enabled: Boolean, hovered: Boolean): WidgetSlice =
    when {
      !enabled -> WidgetSprites.ButtonDisabled
      hovered -> WidgetSprites.ButtonHovered
      else -> WidgetSprites.ButtonNormal
    }

private fun labelColor(enabled: Boolean, hovered: Boolean): Color =
    when {
      !enabled -> TEXT_DISABLED
      hovered -> TEXT_HOVERED
      else -> TEXT_NORMAL
    }

private fun RenderContext.drawCenteredText(text: String, bounds: Rect, color: Color) {
  val x = bounds.x + (bounds.width - textWidth(text)) / 2
  val y = bounds.y + (bounds.height - 8) / 2
  drawText(text, x, y, color, shadow = true)
}

private fun Rect.hovered(context: RenderContext): Boolean = contains(context.mouseX, context.mouseY)

internal fun drawButtonElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.Button,
) {
  drawContainer(context, bounds, element.modifier)
  if (bounds.width <= 0 || bounds.height <= 0) {
    return
  }
  val hovered = element.enabled && bounds.hovered(context)
  context.drawWidgetSlice(
      buttonSlice(element.enabled, hovered),
      bounds.x,
      bounds.y,
      bounds.width,
      bounds.height,
  )
  context.drawCenteredText(
      element.text.formattedString,
      bounds,
      labelColor(element.enabled, hovered),
  )

  if (!element.enabled) {
    return
  }
  context.registerInputTarget(
      InputTarget(
          kind = InputTargetKind.BUTTON,
          bounds = bounds,
          onPress = { _, _, button ->
            if (button != 0) {
              InputPressResult.Ignored
            } else {
              context.playClickSound()
              element.onClick()
              InputPressResult.Consumed
            }
          },
      )
  )
}

internal fun drawCheckboxElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.Checkbox,
) {
  drawContainer(context, bounds, element.modifier)
  if (bounds.width <= 0 || bounds.height <= 0) {
    return
  }
  val hovered = element.enabled && bounds.hovered(context)
  val boxY = bounds.y + (bounds.height - CHECKBOX_BOX) / 2
  context.drawWidgetSlice(WidgetSprites.ButtonNormal, bounds.x, boxY, CHECKBOX_BOX, CHECKBOX_BOX)
  if (element.checked) {
    context.drawText(
        "x",
        bounds.x + 3,
        boxY + 1,
        labelColor(element.enabled, hovered),
        shadow = true,
    )
  }
  context.drawText(
      element.label.formattedString,
      bounds.x + CHECKBOX_BOX + 2,
      boxY + 2,
      labelColor(element.enabled, hovered),
      shadow = true,
  )

  if (!element.enabled) {
    return
  }
  context.registerInputTarget(
      InputTarget(
          kind = InputTargetKind.CHECKBOX,
          bounds = bounds,
          onPress = { _, _, button ->
            if (button != 0) {
              InputPressResult.Ignored
            } else {
              context.playClickSound()
              element.onCheckedChange(!element.checked)
              InputPressResult.Consumed
            }
          },
      )
  )
}

internal fun sliderFraction(element: LayoutElement.Slider): Double {
  val start = minOf(element.valueRangeStart, element.valueRangeEnd)
  val end = maxOf(element.valueRangeStart, element.valueRangeEnd)
  if (end <= start) {
    return 0.0
  }
  return ((element.value - start) / (end - start)).coerceIn(0.0, 1.0)
}

internal fun sliderValueAt(element: LayoutElement.Slider, bounds: Rect, pointerX: Int): Double {
  val start = minOf(element.valueRangeStart, element.valueRangeEnd)
  val end = maxOf(element.valueRangeStart, element.valueRangeEnd)
  val travel = (bounds.width - WidgetSprites.SLIDER_KNOB_WIDTH).coerceAtLeast(1)
  val fraction =
      ((pointerX - bounds.x - WidgetSprites.SLIDER_KNOB_WIDTH / 2).toDouble() / travel).coerceIn(
          0.0,
          1.0,
      )
  val value = start + (end - start) * fraction
  return if (element.showDecimal) value else Math.round(value).toDouble()
}

internal fun sliderLabel(element: LayoutElement.Slider): String {
  val number =
      if (element.showDecimal) String.format(Locale.ROOT, "%.2f", element.value)
      else Math.round(element.value).toString()
  val prefix = if (element.label.isBlank()) "" else "${element.label}: "
  return prefix + number + element.suffix
}

internal fun drawSliderElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.Slider,
) {
  drawContainer(context, bounds, element.modifier)
  if (bounds.width <= 0 || bounds.height <= 0) {
    return
  }
  val hovered = element.enabled && bounds.hovered(context)
  // Track uses the disabled button face like vanilla; the knob is the two 4 px strips at v=66.
  context.drawWidgetSlice(
      WidgetSprites.ButtonDisabled,
      bounds.x,
      bounds.y,
      bounds.width,
      bounds.height,
  )
  val knobX =
      bounds.x +
          (sliderFraction(element) * (bounds.width - WidgetSprites.SLIDER_KNOB_WIDTH)).toInt()
  val knobV = if (hovered) 86 else 66
  context.drawWidgetSprite(0, knobV, 4, WidgetSprites.SLIDER_KNOB_HEIGHT, knobX, bounds.y)
  context.drawWidgetSprite(196, knobV, 4, WidgetSprites.SLIDER_KNOB_HEIGHT, knobX + 4, bounds.y)
  context.drawCenteredText(sliderLabel(element), bounds, labelColor(element.enabled, hovered))

  if (!element.enabled) {
    return
  }
  context.registerInputTarget(
      InputTarget(
          kind = InputTargetKind.SLIDER,
          bounds = bounds,
          onPress = { pressX, _, button ->
            if (button != 0) {
              InputPressResult.Ignored
            } else {
              element.onValueChange(sliderValueAt(element, bounds, pressX))
              InputPressResult.captured(
                  ActivePointerSession(
                      button = button,
                      onDragHandler = { dragX, _ ->
                        element.onValueChange(sliderValueAt(element, bounds, dragX))
                        true
                      },
                      onReleaseHandler = { _, _, _ ->
                        context.playClickSound()
                        true
                      },
                  )
              )
            }
          },
      )
  )
}
