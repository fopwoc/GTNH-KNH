package io.github.fopwoc.mods.framework.ui.compose.layout.text

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawContainer
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.state.TextRange

private const val TEXT_INSET = 4
private const val CURSOR_BLINK_MILLIS = 500L
private val BACKGROUND = Color(0xFF000000)
private val BORDER = Color(0xFFA0A0A0)
private val SELECTION = Color(0x803B7BD8)
private val PLACEHOLDER = Color(0xFF808080)

/** Visible slice of a field's text after horizontal scrolling. */
internal data class TextFieldViewport(
    val scrollOffset: Int,
    val visibleText: String,
)

/**
 * Draws a single-line editable text box (vanilla look) and registers its input target. Editing
 * itself happens in `TextFieldEditor`; this only projects the state onto the screen.
 */
internal fun drawTextFieldElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.TextField,
) {
  drawContainer(context, bounds, element.modifier)
  val state = element.state
  context.textFields.rendered(state, element.style.maxLength)

  val box = bounds.inset(element.modifier.padding)
  if (box.width <= 0 || box.height <= 0) {
    return
  }
  val style = element.style
  if (style.drawBackground) {
    context.fillRect(box.x - 1, box.y - 1, box.x + box.width + 1, box.y + box.height + 1, BORDER)
    context.fillRect(box.x, box.y, box.x + box.width, box.y + box.height, BACKGROUND)
  }

  val textX = box.x + if (style.drawBackground) TEXT_INSET else 0
  val textWidth = (box.width - if (style.drawBackground) TEXT_INSET * 2 else 0).coerceAtLeast(0)
  val textY = box.y + ((box.height - context.lineHeight) / 2).coerceAtLeast(0)
  val editable = element.enabled && state.focused
  val viewport = resolveViewport(context, state, textWidth)
  state.scrollOffset = viewport.scrollOffset
  val visible = viewport.visibleText

  if (state.text.isEmpty() && !state.focused && element.placeholder.isNotEmpty()) {
    context.drawText(element.placeholder, textX, textY, PLACEHOLDER, shadow = true)
  }

  if (editable && !state.selection.collapsed) {
    val range = state.selection
    val left =
        textX +
            context.textWidth(
                visible.take((range.min - viewport.scrollOffset).coerceIn(0, visible.length))
            )
    val right =
        textX +
            context.textWidth(
                visible.take((range.max - viewport.scrollOffset).coerceIn(0, visible.length))
            )
    if (right > left) {
      context.fillRect(left, textY - 1, right, textY + context.lineHeight, SELECTION)
    }
  }

  val textColor = if (element.enabled) style.textColor else style.disabledTextColor
  context.drawText(visible, textX, textY, textColor, shadow = true)

  if (editable && cursorVisible()) {
    val cursorIndex = (state.selection.end - viewport.scrollOffset).coerceIn(0, visible.length)
    val cursorX = textX + context.textWidth(visible.take(cursorIndex))
    if (state.selection.end >= state.text.length) {
      context.drawText("_", cursorX, textY, textColor, shadow = true)
    } else {
      context.fillRect(cursorX, textY - 1, cursorX + 1, textY + context.lineHeight, textColor)
    }
  }

  if (!element.enabled) {
    return
  }
  context.registerInputTarget(
      InputTarget(
          kind = InputTargetKind.TEXT_FIELD,
          bounds = bounds,
          onPress = { clickX, _, button ->
            if (button != 0) {
              InputPressResult.Ignored
            } else {
              context.textFields.focus(state)
              val index = indexAt(context, viewport, textX, clickX)
              state.selection = TextRange(index)
              InputPressResult.captured(
                  ActivePointerSession(
                      button = button,
                      onDragHandler = { dragX, _ ->
                        val anchor = state.selection.start
                        state.selection =
                            TextRange(anchor, indexAt(context, viewport, textX, dragX))
                        true
                      },
                  )
              )
            }
          },
      )
  )
}

/** Scrolls so the cursor stays visible, then trims the text to what fits. */
internal fun resolveViewport(
    metrics: TextMetrics,
    state: TextFieldState,
    availableWidth: Int,
): TextFieldViewport {
  val text = state.text
  val cursor = state.selection.end
  var offset = state.scrollOffset.coerceIn(0, text.length)
  if (cursor < offset) {
    offset = cursor
  } else {
    val visibleFromOffset = metrics.trimToWidth(text.substring(offset), availableWidth).length
    if (cursor > offset + visibleFromOffset) {
      offset =
          cursor -
              metrics.trimToWidth(text.substring(0, cursor), availableWidth, fromEnd = true).length
    }
  }
  return TextFieldViewport(
      scrollOffset = offset,
      visibleText = metrics.trimToWidth(text.substring(offset), availableWidth),
  )
}

/** Character index closest to an x position within the visible text. */
internal fun indexAt(
    metrics: TextMetrics,
    viewport: TextFieldViewport,
    textX: Int,
    pointerX: Int,
): Int {
  val relative = pointerX - textX
  var bestIndex = 0
  var bestDistance = Int.MAX_VALUE
  for (index in 0..viewport.visibleText.length) {
    val distance = kotlin.math.abs(metrics.textWidth(viewport.visibleText.take(index)) - relative)
    if (distance < bestDistance) {
      bestDistance = distance
      bestIndex = index
    }
  }
  return viewport.scrollOffset + bestIndex
}

private fun cursorVisible(): Boolean = (System.currentTimeMillis() / CURSOR_BLINK_MILLIS) % 2 == 0L
