package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.toLayoutProjection
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

internal sealed interface LayoutShape {
    val modifier: Modifier

    data class Box(
        override val modifier: Modifier,
        val contentAlignment: Alignment,
    ) : LayoutShape

    data class Column(
        override val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment,
    ) : LayoutShape

    data class ScrollableColumn(
        override val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment,
    ) : LayoutShape

    data class Row(
        override val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment,
    ) : LayoutShape

    data class LazyColumn(
        override val modifier: Modifier,
        val itemHeight: UiUnit?,
        val itemCount: Int,
        val firstIndex: Int,
    ) : LayoutShape

    data class ScrollableRow(
        override val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment,
    ) : LayoutShape

    data class Text(
        override val modifier: Modifier,
        val text: StyledText,
        val style: TextStyle,
    ) : LayoutShape

    data class Button(
        override val modifier: Modifier,
        val text: StyledText,
    ) : LayoutShape

    data class Checkbox(
        override val modifier: Modifier,
        val label: StyledText,
    ) : LayoutShape

    data class TextField(override val modifier: Modifier) : LayoutShape

    data class Slider(override val modifier: Modifier) : LayoutShape

    data class SelectableList(
        override val modifier: Modifier,
        val items: List<String>,
        val rowHeight: UiUnit,
        val visibleRowCount: Int,
    ) : LayoutShape

    data class Spacer(override val modifier: Modifier) : LayoutShape

    data class GpuCanvas(override val modifier: Modifier) : LayoutShape
}

/** What one composed node is: the data it draws with, and the [shape] the layout rules read. */
internal interface LayoutProjection {
    val modifier: Modifier
    val shape: LayoutShape
}

internal fun ComposeTreeNode.toLayoutShape(): LayoutShape {
    return toLayoutProjection().shape
}
