package io.github.fopwoc.mods.framework.ui.compose.layout.list

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawContainer
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawWithinClip
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.drawScrollIndicator
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.registerScrollThumbTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.registerScrollWheelTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.resolveScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackAxis
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

private const val ROW_TEXT_INSET = 3
private val LIST_BACKGROUND = Color(0xC0101010)
private val SELECTED_FILL = Color(0xFF000000)
private val SELECTED_BORDER = Color(0xFF808080)
private val TEXT = Color.rgb(red = 0xE0, green = 0xE0, blue = 0xE0)
private val TEXT_SELECTED = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF)
private val TEXT_HOVERED = Color.rgb(red = 0xFF, green = 0xF2, blue = 0xA8)

/** Rows the list shows for a given scroll offset; shared by drawing and hit testing. */
internal data class ListRows(
    val rowHeight: Int,
    val firstIndex: Int,
    val lastIndex: Int,
) {
  val indices: IntRange
    get() = firstIndex..lastIndex
}

internal fun visibleRows(
    itemCount: Int,
    rowHeight: Int,
    scroll: Int,
    viewportHeight: Int,
): ListRows {
  val height = rowHeight.coerceAtLeast(1)
  val first = (scroll / height).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
  val last = ((scroll + viewportHeight - 1) / height).coerceIn(first - 1, itemCount - 1)
  return ListRows(rowHeight = height, firstIndex = first, lastIndex = last)
}

internal fun rowAt(rows: ListRows, itemCount: Int, viewportTop: Int, scroll: Int, y: Int): Int? {
  val index = (y - viewportTop + scroll).floorDiv(rows.rowHeight)
  return index.takeIf { it in 0 until itemCount }
}

/** Framework-drawn single-selection list with the usual wheel/thumb scrolling. */
internal fun drawSelectableListElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.SelectableList,
) {
  drawContainer(context, bounds, element.modifier)
  val rowHeight = element.rowHeight.resolved.coerceAtLeast(context.lineHeight + 2)
  val metrics =
      resolveScrollMetrics(
          bounds = bounds,
          modifier = element.modifier,
          contentMainAxisSize = element.items.size * rowHeight,
          state = element.scrollState,
          axis = StackAxis.VERTICAL,
      )
  val viewport = metrics.viewportBounds
  if (viewport.width <= 0 || viewport.height <= 0) {
    return
  }
  val scroll = element.scrollState.value
  val rows = visibleRows(element.items.size, rowHeight, scroll, viewport.height)
  val hoveredIndex =
      if (viewport.contains(context.mouseX, context.mouseY)) {
        rowAt(rows, element.items.size, viewport.y, scroll, context.mouseY)
      } else {
        null
      }

  context.fillRect(
      viewport.x,
      viewport.y,
      viewport.x + viewport.width,
      viewport.y + viewport.height,
      LIST_BACKGROUND,
  )
  registerScrollWheelTarget(context, metrics)
  drawWithinClip(context, viewport) {
    for (index in rows.indices) {
      val top = viewport.y + index * rowHeight - scroll
      val bottom = top + rowHeight
      val selected = index == element.selectedIndex
      if (selected) {
        context.fillRect(viewport.x, top, viewport.x + viewport.width, bottom, SELECTED_BORDER)
        context.fillRect(
            viewport.x + 1,
            top + 1,
            viewport.x + viewport.width - 1,
            bottom - 1,
            SELECTED_FILL,
        )
      }
      val color =
          when {
            selected -> TEXT_SELECTED
            index == hoveredIndex -> TEXT_HOVERED
            else -> TEXT
          }
      val text = context.trimToWidth(element.items[index], viewport.width - ROW_TEXT_INSET * 2)
      val textY = top + ((rowHeight - context.lineHeight) / 2).coerceAtLeast(0)
      context.drawText(text, viewport.x + ROW_TEXT_INSET, textY, color, shadow = true)
    }
  }
  drawScrollIndicator(context, metrics)
  registerScrollThumbTarget(context, metrics)

  context.registerInputTarget(
      InputTarget(
          kind = InputTargetKind.SELECTABLE_LIST,
          bounds = viewport,
          onPress = { _, clickY, button ->
            val index =
                if (button == 0) rowAt(rows, element.items.size, viewport.y, scroll, clickY)
                else null
            if (index == null) {
              InputPressResult.Ignored
            } else {
              if (index != element.selectedIndex) {
                element.onSelectedIndexChange(index)
              }
              InputPressResult.Consumed
            }
          },
      )
  )
}
