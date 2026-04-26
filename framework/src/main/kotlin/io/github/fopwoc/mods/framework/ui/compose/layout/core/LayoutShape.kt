package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.toLayoutProjection
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

internal sealed interface LayoutShape {
    data class Box(
        val modifier: Modifier,
        val contentAlignment: Alignment
    ) : LayoutShape

    data class Column(
        val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment
    ) : LayoutShape

    data class ScrollableColumn(
        val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment
    ) : LayoutShape

    data class Row(
        val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment
    ) : LayoutShape

    data class ScrollableRow(
        val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment
    ) : LayoutShape

    data class Text(
        val modifier: Modifier,
        val text: StyledText,
        val style: TextStyle
    ) : LayoutShape

    data class Button(
        val modifier: Modifier,
        val text: StyledText
    ) : LayoutShape

    data class Checkbox(
        val modifier: Modifier,
        val label: StyledText
    ) : LayoutShape

    data class TextField(
        val modifier: Modifier
    ) : LayoutShape

    data class Slider(
        val modifier: Modifier
    ) : LayoutShape

    data class SelectableList(
        val modifier: Modifier,
        val items: List<String>,
        val rowHeight: UiUnit,
        val visibleRowCount: Int
    ) : LayoutShape

    data class Spacer(
        val modifier: Modifier
    ) : LayoutShape
}

internal interface LayoutProjection {
    val modifier: Modifier
    val shape: LayoutShape

    fun toLayoutElement(children: List<LayoutElement> = emptyList()): LayoutElement
}

internal class LayoutElementProjection(
    override val modifier: Modifier,
    override val shape: LayoutShape,
    val children: List<LayoutElement> = emptyList(),
    private val createElement: (List<LayoutElement>) -> LayoutElement
) : LayoutProjection {
    override fun toLayoutElement(children: List<LayoutElement>): LayoutElement = createElement(children)
}

internal fun LayoutElement.toLayoutProjection(): LayoutElementProjection {
    return when (this) {
        is LayoutElement.Box -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Box(
                modifier = modifier,
                contentAlignment = contentAlignment
            ),
            children = children,
            createElement = { projectedChildren ->
                LayoutElement.Box(
                    modifier = modifier,
                    contentAlignment = contentAlignment,
                    children = projectedChildren
                )
            }
        )
        is LayoutElement.Column -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Column(
                modifier = modifier,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment
            ),
            children = children,
            createElement = { projectedChildren ->
                LayoutElement.Column(
                    modifier = modifier,
                    verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment,
                    children = projectedChildren
                )
            }
        )
        is LayoutElement.ScrollableColumn -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.ScrollableColumn(
                modifier = modifier,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment
            ),
            children = children,
            createElement = { projectedChildren ->
                LayoutElement.ScrollableColumn(
                    modifier = modifier,
                    verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment,
                    state = state,
                    scrollValue = scrollValue,
                    children = projectedChildren
                )
            }
        )
        is LayoutElement.Row -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Row(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment
            ),
            children = children,
            createElement = { projectedChildren ->
                LayoutElement.Row(
                    modifier = modifier,
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = verticalAlignment,
                    children = projectedChildren
                )
            }
        )
        is LayoutElement.ScrollableRow -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.ScrollableRow(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment
            ),
            children = children,
            createElement = { projectedChildren ->
                LayoutElement.ScrollableRow(
                    modifier = modifier,
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = verticalAlignment,
                    state = state,
                    scrollValue = scrollValue,
                    children = projectedChildren
                )
            }
        )
        is LayoutElement.Text -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Text(modifier = modifier, text = text, style = style),
            createElement = { LayoutElement.Text(modifier = modifier, text = text, style = style) }
        )
        is LayoutElement.Button -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Button(modifier = modifier, text = text),
            createElement = {
                LayoutElement.Button(
                    modifier = modifier,
                    hostKey = hostKey,
                    text = text,
                    enabled = enabled,
                    onClick = onClick
                )
            }
        )
        is LayoutElement.Checkbox -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Checkbox(modifier = modifier, label = label),
            createElement = {
                LayoutElement.Checkbox(
                    modifier = modifier,
                    hostKey = hostKey,
                    label = label,
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange
                )
            }
        )
        is LayoutElement.TextField -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.TextField(modifier = modifier),
            createElement = {
                LayoutElement.TextField(
                    modifier = modifier,
                    hostKey = hostKey,
                    state = state,
                    placeholder = placeholder,
                    enabled = enabled,
                    style = style
                )
            }
        )
        is LayoutElement.Slider -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Slider(modifier = modifier),
            createElement = {
                LayoutElement.Slider(
                    modifier = modifier,
                    hostKey = hostKey,
                    value = value,
                    valueRangeStart = valueRangeStart,
                    valueRangeEnd = valueRangeEnd,
                    label = label,
                    suffix = suffix,
                    enabled = enabled,
                    showDecimal = showDecimal,
                    onValueChange = onValueChange
                )
            }
        )
        is LayoutElement.SelectableList -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.SelectableList(
                modifier = modifier,
                items = items,
                rowHeight = rowHeight,
                visibleRowCount = visibleRowCount
            ),
            createElement = {
                LayoutElement.SelectableList(
                    modifier = modifier,
                    hostKey = hostKey,
                    items = items,
                    selectedIndex = selectedIndex,
                    rowHeight = rowHeight,
                    visibleRowCount = visibleRowCount,
                    onSelectedIndexChange = onSelectedIndexChange
                )
            }
        )
        is LayoutElement.Spacer -> LayoutElementProjection(
            modifier = modifier,
            shape = LayoutShape.Spacer(modifier = modifier),
            createElement = { LayoutElement.Spacer(modifier = modifier) }
        )
    }
}

internal fun LayoutElement.toLayoutShape(): LayoutShape {
    return toLayoutProjection().shape
}

internal fun ComposeTreeNode.toLayoutShape(): LayoutShape {
    return toLayoutProjection().shape
}

