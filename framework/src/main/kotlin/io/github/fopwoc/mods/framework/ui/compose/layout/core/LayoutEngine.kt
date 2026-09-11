package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.layout.box.BoxPlacementSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.box.placeBoxChildren
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureButtonNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureCheckboxNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureSliderNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureTextFieldNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.list.measureSelectableListNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.list.measureSpacerNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.ScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.ScrollbarGutterWidth
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.resolveScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackAxis
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackMeasureSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackMeasurement
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackPlacementSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.measureStack
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.placeStackChildren
import io.github.fopwoc.mods.framework.ui.compose.layout.text.measureTextNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.arrange
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.measuredSpacing
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentWidth
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowAlignment
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

/**
 * Measures and places a UI tree. The tree can be the live compose node tree or an immutable
 * [LayoutElement] tree (used by tests); both are reduced to [LayoutShape]s so the layout rules
 * exist once.
 */
internal object LayoutEngine {
  internal fun layout(
      root: ComposeTreeNode,
      metrics: TextMetrics,
      viewportWidth: Int,
      viewportHeight: Int,
  ): LayoutNode = layout(ComposeSource, root, metrics, viewportWidth, viewportHeight)

  internal fun layout(
      root: LayoutElement,
      metrics: TextMetrics,
      viewportWidth: Int,
      viewportHeight: Int,
  ): LayoutNode = layout(ElementSource, root, metrics, viewportWidth, viewportHeight)

  internal fun refreshPlacement(root: LayoutNode): LayoutNode {
    return place(root, x = root.bounds.x, y = root.bounds.y)
  }

  /** Adapts one tree representation to the measurement pass. */
  private interface Source<T> {
    fun shape(item: T): LayoutShape

    fun modifier(item: T): Modifier

    fun children(item: T): List<T>

    fun node(
        item: T,
        size: Size,
        children: List<LayoutNode>,
        contentMainAxisSize: Int = 0,
    ): LayoutNode
  }

  private object ComposeSource : Source<ComposeTreeNode> {
    override fun shape(item: ComposeTreeNode): LayoutShape = item.toLayoutShape()

    override fun modifier(item: ComposeTreeNode): Modifier = item.modifier

    override fun children(item: ComposeTreeNode): List<ComposeTreeNode> = item.children

    override fun node(
        item: ComposeTreeNode,
        size: Size,
        children: List<LayoutNode>,
        contentMainAxisSize: Int,
    ): LayoutNode =
        LayoutNode(
            composeNode = item,
            bounds = Rect(0, 0, size.width, size.height),
            children = children,
            occupiedSize = size,
            contentMainAxisSize = contentMainAxisSize,
        )
  }

  private object ElementSource : Source<LayoutElement> {
    override fun shape(item: LayoutElement): LayoutShape = item.toLayoutShape()

    override fun modifier(item: LayoutElement): Modifier = item.modifier

    override fun children(item: LayoutElement): List<LayoutElement> =
        when (item) {
          is LayoutElement.Box -> item.children
          is LayoutElement.Column -> item.children
          is LayoutElement.ScrollableColumn -> item.children
          is LayoutElement.Row -> item.children
          is LayoutElement.ScrollableRow -> item.children
          is LayoutElement.LazyColumn -> item.children
          is LayoutElement.Text,
          is LayoutElement.Button,
          is LayoutElement.Checkbox,
          is LayoutElement.TextField,
          is LayoutElement.Slider,
          is LayoutElement.SelectableList,
          is LayoutElement.Spacer -> emptyList()
        }

    override fun node(
        item: LayoutElement,
        size: Size,
        children: List<LayoutNode>,
        contentMainAxisSize: Int,
    ): LayoutNode =
        LayoutNode(
            element = item,
            bounds = Rect(0, 0, size.width, size.height),
            children = children,
            occupiedSize = size,
            contentMainAxisSize = contentMainAxisSize,
        )
  }

  private fun <T> layout(
      source: Source<T>,
      root: T,
      metrics: TextMetrics,
      viewportWidth: Int,
      viewportHeight: Int,
  ): LayoutNode {
    return measure(
            source = source,
            item = root,
            metrics = metrics,
            maxWidth = viewportWidth.coerceAtLeast(0),
            maxHeight = viewportHeight.coerceAtLeast(0),
        )
        .also { place(it, x = 0, y = 0) }
  }

  private fun <T> measure(
      source: Source<T>,
      item: T,
      metrics: TextMetrics,
      maxWidth: Int,
      maxHeight: Int,
  ): LayoutNode {
    val clampedMaxWidth = maxWidth.coerceAtLeast(0)
    val clampedMaxHeight = maxHeight.coerceAtLeast(0)
    val childCount = source.children(item).size
    return when (val shape = source.shape(item)) {
      is LayoutShape.Box ->
          measureBox(source, item, shape.modifier, metrics, clampedMaxWidth, clampedMaxHeight)
      is LayoutShape.Column ->
          measureStackContainer(
              source = source,
              item = item,
              modifier = shape.modifier,
              axis = StackAxis.VERTICAL,
              spacing = shape.verticalArrangement.measuredSpacing(childCount),
              metrics = metrics,
              maxWidth = clampedMaxWidth,
              maxHeight = clampedMaxHeight,
          )
      is LayoutShape.Row ->
          measureStackContainer(
              source = source,
              item = item,
              modifier = shape.modifier,
              axis = StackAxis.HORIZONTAL,
              spacing = shape.horizontalArrangement.measuredSpacing(childCount),
              metrics = metrics,
              maxWidth = clampedMaxWidth,
              maxHeight = clampedMaxHeight,
          )
      is LayoutShape.ScrollableColumn ->
          measureScrollableContainer(
              source = source,
              item = item,
              modifier = shape.modifier,
              axis = StackAxis.VERTICAL,
              spacing = shape.verticalArrangement.measuredSpacing(childCount),
              metrics = metrics,
              maxWidth = clampedMaxWidth,
              maxHeight = clampedMaxHeight,
          )
      is LayoutShape.ScrollableRow ->
          measureScrollableContainer(
              source = source,
              item = item,
              modifier = shape.modifier,
              axis = StackAxis.HORIZONTAL,
              spacing = shape.horizontalArrangement.measuredSpacing(childCount),
              metrics = metrics,
              maxWidth = clampedMaxWidth,
              maxHeight = clampedMaxHeight,
          )
      is LayoutShape.LazyColumn ->
          measureLazyColumn(source, item, shape, metrics, clampedMaxWidth, clampedMaxHeight)
      is LayoutShape.Text ->
          measureLeaf(
              source,
              item,
              measureTextNaturalSize(
                  shape.modifier,
                  shape.text,
                  shape.style,
                  metrics,
                  clampedMaxWidth,
              ),
              clampedMaxWidth,
              clampedMaxHeight,
          )
      is LayoutShape.Button ->
          measureLeaf(
              source,
              item,
              measureButtonNaturalSize(shape.modifier, shape.text, metrics),
              clampedMaxWidth,
              clampedMaxHeight,
          )
      is LayoutShape.Checkbox ->
          measureLeaf(
              source,
              item,
              measureCheckboxNaturalSize(shape.modifier, shape.label, metrics),
              clampedMaxWidth,
              clampedMaxHeight,
          )
      is LayoutShape.TextField ->
          measureLeaf(
              source,
              item,
              measureTextFieldNaturalSize(shape.modifier),
              clampedMaxWidth,
              clampedMaxHeight,
          )
      is LayoutShape.Slider ->
          measureLeaf(
              source,
              item,
              measureSliderNaturalSize(shape.modifier),
              clampedMaxWidth,
              clampedMaxHeight,
          )
      is LayoutShape.SelectableList ->
          measureLeaf(
              source,
              item,
              measureSelectableListNaturalSize(
                  modifier = shape.modifier,
                  items = shape.items,
                  rowHeight = shape.rowHeight,
                  visibleRowCount = shape.visibleRowCount,
                  metrics = metrics,
              ),
              clampedMaxWidth,
              clampedMaxHeight,
          )
      is LayoutShape.Spacer ->
          measureLeaf(
              source,
              item,
              measureSpacerNaturalSize(shape.modifier),
              clampedMaxWidth,
              clampedMaxHeight,
          )
    }
  }

  private fun <T> measureBox(
      source: Source<T>,
      item: T,
      modifier: Modifier,
      metrics: TextMetrics,
      maxWidth: Int,
      maxHeight: Int,
  ): LayoutNode {
    val padding = modifier.padding
    val innerWidth = availableInnerWidth(modifier, maxWidth)
    val innerHeight = availableInnerHeight(modifier, maxHeight)
    val children = source.children(item)
    val initiallyMeasuredChildren = children.map { child ->
      measure(source, child, metrics, innerWidth, innerHeight)
    }
    val contentWidth =
        initiallyMeasuredChildren.maxOfOrNull { child ->
          if (child.modifier.boxMatchesParentWidth) 0 else child.size.width
        } ?: 0
    val contentHeight =
        initiallyMeasuredChildren.maxOfOrNull { child ->
          if (child.modifier.boxMatchesParentHeight) 0 else child.size.height
        } ?: 0
    val resolvedSize =
        resolveSize(
            modifier = modifier,
            naturalWidth = contentWidth + padding.horizontalValue,
            naturalHeight = contentHeight + padding.verticalValue,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
        )
    val resolvedInnerWidth = (resolvedSize.width - padding.horizontalValue).coerceAtLeast(0)
    val resolvedInnerHeight = (resolvedSize.height - padding.verticalValue).coerceAtLeast(0)
    // Children matching the parent are measured again once the box knows its own size.
    val measuredChildren =
        children.zip(initiallyMeasuredChildren).map { (child, initialMeasurement) ->
          val childModifier = source.modifier(child)
          if (!childModifier.boxMatchesParentWidth && !childModifier.boxMatchesParentHeight) {
            initialMeasurement
          } else {
            val remeasuredChild =
                measure(source, child, metrics, resolvedInnerWidth, resolvedInnerHeight)
            val resolvedChildSize =
                Size(
                    width =
                        if (childModifier.boxMatchesParentWidth) resolvedInnerWidth
                        else remeasuredChild.size.width,
                    height =
                        if (childModifier.boxMatchesParentHeight) resolvedInnerHeight
                        else remeasuredChild.size.height,
                )
            remeasuredChild.apply {
              updateMeasuredSize(size = resolvedChildSize, occupiedSize = resolvedChildSize)
            }
          }
        }
    return source.node(item, resolvedSize, measuredChildren)
  }

  private fun <T> measureStackContainer(
      source: Source<T>,
      item: T,
      modifier: Modifier,
      axis: StackAxis,
      spacing: Int,
      metrics: TextMetrics,
      maxWidth: Int,
      maxHeight: Int,
  ): LayoutNode {
    val padding = modifier.padding
    val stackMeasurement =
        measureStack(
            spec =
                StackMeasureSpec(
                    axis = axis,
                    maxWidth = availableInnerWidth(modifier, maxWidth),
                    maxHeight = availableInnerHeight(modifier, maxHeight),
                    spacing = spacing,
                ),
            children = source.children(item),
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
              measure(source, child, childMetrics, childMaxWidth, childMaxHeight)
            },
            childModifier = source::modifier,
        )
    val size =
        resolveSize(
            modifier = modifier,
            naturalWidth = stackMeasurement.naturalWidth(axis) + padding.horizontalValue,
            naturalHeight = stackMeasurement.naturalHeight(axis) + padding.verticalValue,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
        )
    return source.node(
        item = item,
        size = size,
        children = stackMeasurement.children,
        contentMainAxisSize = stackMeasurement.contentMainAxisSize,
    )
  }

  private fun <T> measureScrollableContainer(
      source: Source<T>,
      item: T,
      modifier: Modifier,
      axis: StackAxis,
      spacing: Int,
      metrics: TextMetrics,
      maxWidth: Int,
      maxHeight: Int,
  ): LayoutNode {
    val padding = modifier.padding
    val innerWidth = availableInnerWidth(modifier, maxWidth)
    val innerHeight = availableInnerHeight(modifier, maxHeight)
    val mainAxisInner = if (axis == StackAxis.VERTICAL) innerHeight else innerWidth
    val crossAxisInner = if (axis == StackAxis.VERTICAL) innerWidth else innerHeight
    val children = source.children(item)

    fun measureContent(crossAxisLimit: Int): StackMeasurement =
        measureStack(
            spec =
                StackMeasureSpec(
                    axis = axis,
                    maxWidth = if (axis == StackAxis.VERTICAL) crossAxisLimit else innerWidth,
                    maxHeight = if (axis == StackAxis.VERTICAL) innerHeight else crossAxisLimit,
                    spacing = spacing,
                    isMainAxisBounded = false,
                ),
            children = children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
              measure(source, child, childMetrics, childMaxWidth, childMaxHeight)
            },
            childModifier = source::modifier,
        )

    // Measure once unconstrained; if the content overflows, measure again with the scrollbar
    // gutter taken out of the cross axis.
    val initialMeasurement = measureContent(crossAxisInner)
    val needsScrollbar =
        initialMeasurement.contentMainAxisSize > mainAxisInner && crossAxisInner > 0
    val stackMeasurement =
        if (needsScrollbar) {
          measureContent((crossAxisInner - ScrollbarGutterWidth).coerceAtLeast(0))
        } else {
          initialMeasurement
        }
    val contentMain = stackMeasurement.contentMainAxisSize
    val contentCross = stackMeasurement.contentCrossAxisSize
    val gutter =
        if (contentMain > mainAxisInner) ScrollbarGutterWidth.coerceAtMost(crossAxisInner) else 0
    val size =
        when (axis) {
          StackAxis.VERTICAL ->
              resolveSize(
                  modifier = modifier,
                  naturalWidth = contentCross + padding.horizontalValue + gutter,
                  naturalHeight = (contentMain + padding.verticalValue).coerceAtMost(maxHeight),
                  maxWidth = maxWidth,
                  maxHeight = maxHeight,
              )
          StackAxis.HORIZONTAL ->
              resolveSize(
                  modifier = modifier,
                  naturalWidth = (contentMain + padding.horizontalValue).coerceAtMost(maxWidth),
                  naturalHeight = contentCross + padding.verticalValue + gutter,
                  maxWidth = maxWidth,
                  maxHeight = maxHeight,
              )
        }
    return source.node(
        item = item,
        size = size,
        children = stackMeasurement.children,
        contentMainAxisSize = contentMain,
    )
  }

  /**
   * Children are the composed window; each takes exactly one item slot. The full content height
   * comes from the item count so scrolling covers items that are not composed yet.
   */
  private fun <T> measureLazyColumn(
      source: Source<T>,
      item: T,
      shape: LayoutShape.LazyColumn,
      metrics: TextMetrics,
      maxWidth: Int,
      maxHeight: Int,
  ): LayoutNode {
    val modifier = shape.modifier
    val padding = modifier.padding
    val itemHeight = shape.itemHeight.resolved.coerceAtLeast(1)
    val contentHeight = shape.itemCount * itemHeight
    val innerWidth = availableInnerWidth(modifier, maxWidth)
    val innerHeight = availableInnerHeight(modifier, maxHeight)
    val gutter =
        if (contentHeight > innerHeight) ScrollbarGutterWidth.coerceAtMost(innerWidth) else 0
    val rowWidth = (innerWidth - gutter).coerceAtLeast(0)
    val children =
        source.children(item).map { child ->
          measure(source, child, metrics, rowWidth, itemHeight).apply {
            updateMeasuredSize(Size(size.width, itemHeight), Size(rowWidth, itemHeight))
          }
        }
    val contentWidth = children.maxOfOrNull { it.size.width } ?: 0
    val size =
        resolveSize(
            modifier = modifier,
            naturalWidth = contentWidth + padding.horizontalValue + gutter,
            naturalHeight = (contentHeight + padding.verticalValue).coerceAtMost(maxHeight),
            maxWidth = maxWidth,
            maxHeight = maxHeight,
        )
    return source.node(item, size, children, contentMainAxisSize = contentHeight)
  }

  private fun <T> measureLeaf(
      source: Source<T>,
      item: T,
      naturalSize: Size,
      maxWidth: Int,
      maxHeight: Int,
  ): LayoutNode {
    val size =
        resolveSize(
            source.modifier(item),
            naturalSize.width,
            naturalSize.height,
            maxWidth,
            maxHeight,
        )
    return source.node(item, size, emptyList())
  }

  private fun StackMeasurement.naturalWidth(axis: StackAxis): Int =
      if (axis == StackAxis.HORIZONTAL) contentMainAxisSize else contentCrossAxisSize

  private fun StackMeasurement.naturalHeight(axis: StackAxis): Int =
      if (axis == StackAxis.VERTICAL) contentMainAxisSize else contentCrossAxisSize

  private fun place(measured: LayoutNode, x: Int, y: Int): LayoutNode {
    measured.placeAt(x, y)
    val bounds = measured.bounds
    val modifier = measured.modifier
    val scrollMetrics =
        when (val shape = measured.shape) {
          is LayoutShape.Box -> {
            placeBoxChildren(
                children = measured.children,
                spec =
                    BoxPlacementSpec(
                        contentRect = bounds.inset(modifier.padding),
                        contentAlignment = shape.contentAlignment,
                    ),
                placeChild = ::place,
            )
            null
          }
          is LayoutShape.Column -> {
            placeColumn(
                measured,
                contentRect = bounds.inset(modifier.padding),
                arrangement = shape.verticalArrangement,
                alignment = shape.horizontalAlignment,
                translation = 0,
            )
            null
          }
          is LayoutShape.Row -> {
            placeRow(
                measured,
                contentRect = bounds.inset(modifier.padding),
                arrangement = shape.horizontalArrangement,
                alignment = shape.verticalAlignment,
                translation = 0,
            )
            null
          }
          is LayoutShape.ScrollableColumn -> {
            val metrics = resolveScrollMetrics(measured, StackAxis.VERTICAL)
            placeColumn(
                measured,
                contentRect = metrics.viewportBounds,
                arrangement = shape.verticalArrangement,
                alignment = shape.horizontalAlignment,
                translation = -metrics.state.value,
            )
            metrics
          }
          is LayoutShape.LazyColumn -> {
            val metrics = resolveScrollMetrics(measured, StackAxis.VERTICAL)
            val itemHeight = shape.itemHeight.resolved.coerceAtLeast(1)
            val viewport = metrics.viewportBounds
            measured.children.forEachIndexed { offset, child ->
              val childModifier = child.modifier
              place(
                  child,
                  viewport.x +
                      alignedOffset(
                          childModifier.columnAlignment ?: HorizontalAlignment.START,
                          viewport.width,
                          child.size.width,
                      ),
                  viewport.y + (shape.firstIndex + offset) * itemHeight - metrics.state.value,
              )
            }
            measured.lazyListState?.publishWindow(itemHeight, metrics.state.value, viewport.height)
            metrics
          }
          is LayoutShape.ScrollableRow -> {
            val metrics = resolveScrollMetrics(measured, StackAxis.HORIZONTAL)
            placeRow(
                measured,
                contentRect = metrics.viewportBounds,
                arrangement = shape.horizontalArrangement,
                alignment = shape.verticalAlignment,
                translation = -metrics.state.value,
            )
            metrics
          }
          is LayoutShape.Text,
          is LayoutShape.Button,
          is LayoutShape.Checkbox,
          is LayoutShape.TextField,
          is LayoutShape.Slider,
          is LayoutShape.SelectableList,
          is LayoutShape.Spacer -> null
        }
    measured.updateScrollMetrics(scrollMetrics)
    return measured
  }

  private fun resolveScrollMetrics(measured: LayoutNode, axis: StackAxis): ScrollMetrics =
      resolveScrollMetrics(
          bounds = measured.bounds,
          modifier = measured.modifier,
          contentMainAxisSize = measured.contentMainAxisSize,
          state = checkNotNull(measured.scrollState) { "Scrollable shape without scroll state" },
          axis = axis,
      )

  private fun placeColumn(
      measured: LayoutNode,
      contentRect: Rect,
      arrangement: VerticalArrangement,
      alignment: HorizontalAlignment,
      translation: Int,
  ) {
    placeStackChildren(
        children = measured.children,
        spec =
            StackPlacementSpec(
                axis = StackAxis.VERTICAL,
                contentRect = contentRect,
                mainAxisPositions =
                    arrangement.arrange(
                        totalSize = contentRect.height,
                        childSizes = measured.children.map { it.occupiedSize.height },
                    ),
                mainAxisTranslation = translation,
                crossAxisOffset = { child, availableCrossAxisSize ->
                  alignedOffset(
                      alignment = child.modifier.columnAlignment ?: alignment,
                      available = availableCrossAxisSize,
                      childSize = child.size.width,
                  )
                },
            ),
        placeChild = ::place,
    )
  }

  private fun placeRow(
      measured: LayoutNode,
      contentRect: Rect,
      arrangement: HorizontalArrangement,
      alignment: VerticalAlignment,
      translation: Int,
  ) {
    placeStackChildren(
        children = measured.children,
        spec =
            StackPlacementSpec(
                axis = StackAxis.HORIZONTAL,
                contentRect = contentRect,
                mainAxisPositions =
                    arrangement.arrange(
                        totalSize = contentRect.width,
                        childSizes = measured.children.map { it.occupiedSize.width },
                    ),
                mainAxisTranslation = translation,
                crossAxisOffset = { child, availableCrossAxisSize ->
                  alignedOffset(
                      alignment = child.modifier.rowAlignment ?: alignment,
                      available = availableCrossAxisSize,
                      childSize = child.size.height,
                  )
                },
            ),
        placeChild = ::place,
    )
  }
}

private fun LazyListState.publishWindow(itemHeight: Int, scroll: Int, viewportHeight: Int) {
  itemHeightPx = itemHeight
  val first = scroll / itemHeight
  val count = (viewportHeight + itemHeight - 1) / itemHeight + 1
  if (firstVisibleItemIndex != first) {
    firstVisibleItemIndex = first
  }
  if (visibleItemCount != count) {
    visibleItemCount = count
  }
}
