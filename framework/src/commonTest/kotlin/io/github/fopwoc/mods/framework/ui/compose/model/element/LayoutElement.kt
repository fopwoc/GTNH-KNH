package io.github.fopwoc.mods.framework.ui.compose.model.element

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode
import io.github.fopwoc.mods.framework.ui.compose.node.CheckboxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.GpuCanvasNode
import io.github.fopwoc.mods.framework.ui.compose.node.LazyColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.ScrollableColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.node.SliderNode
import io.github.fopwoc.mods.framework.ui.compose.node.SpacerNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextFieldNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

/**
 * An immutable UI tree for layout tests, built without running a composition; [toNode] turns it
 * into the compose nodes the layout engine reads.
 */
internal sealed class LayoutElement(open val modifier: Modifier) {
    abstract fun toNode(): ComposeTreeNode

    protected fun ComposeTreeNode.withChildren(children: List<LayoutElement>): ComposeTreeNode =
        apply {
            this.children += children.map(LayoutElement::toNode)
        }

    data class Box(
        override val modifier: Modifier,
        val contentAlignment: Alignment,
        val children: List<LayoutElement>,
    ) : LayoutElement(modifier) {
        override fun toNode() = BoxNode(modifier, contentAlignment).withChildren(children)
    }

    data class Column(
        override val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment,
        val children: List<LayoutElement>,
    ) : LayoutElement(modifier) {
        override fun toNode() =
            ColumnNode(modifier, verticalArrangement, horizontalAlignment).withChildren(children)
    }

    data class ScrollableColumn(
        override val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment,
        val state: ScrollState,
        val children: List<LayoutElement>,
    ) : LayoutElement(modifier) {
        override fun toNode() =
            ScrollableColumnNode(modifier, verticalArrangement, horizontalAlignment, state)
                .withChildren(children)
    }

    data class ScrollableRow(
        override val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment,
        val state: ScrollState,
        val children: List<LayoutElement>,
    ) : LayoutElement(modifier) {
        override fun toNode() =
            RowNode(modifier.horizontalScroll(state), horizontalArrangement, verticalAlignment)
                .withChildren(children)
    }

    data class Row(
        override val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment,
        val children: List<LayoutElement>,
    ) : LayoutElement(modifier) {
        override fun toNode() =
            RowNode(modifier, horizontalArrangement, verticalAlignment).withChildren(children)
    }

    data class LazyColumn(
        override val modifier: Modifier,
        val itemHeight: UiUnit?,
        val itemCount: Int,
        val firstIndex: Int,
        val state: LazyListState,
        val children: List<LayoutElement>,
    ) : LayoutElement(modifier) {
        override fun toNode() =
            LazyColumnNode(modifier, state, itemHeight, itemCount, firstIndex)
                .withChildren(children)
    }

    data class Text(
        override val modifier: Modifier,
        val text: StyledText,
        val style: TextStyle,
    ) : LayoutElement(modifier) {
        override fun toNode() = TextNode(modifier, text, style)
    }

    class Button(
        override val modifier: Modifier,
        val text: StyledText,
        val enabled: Boolean,
        val onClick: () -> Unit,
    ) : LayoutElement(modifier) {
        override fun toNode() = ButtonNode(modifier, text, enabled, onClick)
    }

    class Checkbox(
        override val modifier: Modifier,
        val label: StyledText,
        val checked: Boolean,
        val enabled: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : LayoutElement(modifier) {
        override fun toNode() = CheckboxNode(modifier, label, checked, enabled, onCheckedChange)
    }

    data class TextField(
        override val modifier: Modifier,
        val state: TextFieldState,
        val placeholder: String,
        val enabled: Boolean,
        val style: TextFieldStyle,
    ) : LayoutElement(modifier) {
        override fun toNode() = TextFieldNode(modifier, state, placeholder, enabled, style)
    }

    class Slider(
        override val modifier: Modifier,
        val value: Double,
        val valueRangeStart: Double,
        val valueRangeEnd: Double,
        val label: String,
        val suffix: String,
        val enabled: Boolean,
        val showDecimal: Boolean,
        val onValueChange: (Double) -> Unit,
    ) : LayoutElement(modifier) {
        override fun toNode() =
            SliderNode(
                modifier,
                value,
                valueRangeStart,
                valueRangeEnd,
                label,
                suffix,
                enabled,
                showDecimal,
                onValueChange,
            )
    }

    class SelectableList(
        override val modifier: Modifier,
        val items: List<String>,
        val selectedIndices: Set<Int>,
        val rowHeight: UiUnit,
        val visibleRowCount: Int,
        val onItemClick: (index: Int, modifiers: KeyModifiers) -> Unit,
    ) : LayoutElement(modifier) {
        override fun toNode() =
            SelectableListNode(
                modifier,
                items,
                selectedIndices,
                rowHeight,
                visibleRowCount,
                onItemClick,
            )
    }

    data class Spacer(override val modifier: Modifier) : LayoutElement(modifier) {
        override fun toNode() = SpacerNode(modifier)
    }

    class GpuCanvas(override val modifier: Modifier, val state: GpuCanvasState) :
        LayoutElement(modifier) {
        override fun toNode() = GpuCanvasNode(modifier, state)
    }
}

/** Lays out a test tree through the compose nodes it stands for. */
internal fun LayoutEngine.layout(
    root: LayoutElement,
    metrics: TextMetrics,
    viewportWidth: Int,
    viewportHeight: Int,
): LayoutNode = layout(root.toNode(), metrics, viewportWidth, viewportHeight)
