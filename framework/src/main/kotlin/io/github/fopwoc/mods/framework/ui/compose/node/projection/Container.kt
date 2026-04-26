package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutProjection
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutShape
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.horizontalScrollState
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.verticalScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState

internal sealed interface ComposeContainerProjection : LayoutProjection {
    override val modifier: Modifier

    override val shape: LayoutShape
        get() = when (this) {
            is Box -> LayoutShape.Box(
                modifier = modifier,
                contentAlignment = contentAlignment
            )
            is Column -> {
                if (scrollState != null) {
                    LayoutShape.ScrollableColumn(
                        modifier = modifier,
                        verticalArrangement = verticalArrangement,
                        horizontalAlignment = horizontalAlignment
                    )
                } else {
                    LayoutShape.Column(
                        modifier = modifier,
                        verticalArrangement = verticalArrangement,
                        horizontalAlignment = horizontalAlignment
                    )
                }
            }
            is Row -> {
                if (scrollState != null) {
                    LayoutShape.ScrollableRow(
                        modifier = modifier,
                        horizontalArrangement = horizontalArrangement,
                        verticalAlignment = verticalAlignment
                    )
                } else {
                    LayoutShape.Row(
                        modifier = modifier,
                        horizontalArrangement = horizontalArrangement,
                        verticalAlignment = verticalAlignment
                    )
                }
            }
        }

    override fun toLayoutElement(children: List<LayoutElement>): LayoutElement = when (this) {
        is Box -> LayoutElement.Box(
            modifier = modifier,
            contentAlignment = contentAlignment,
            children = children
        )
        is Column -> {
            if (scrollState != null) {
                LayoutElement.ScrollableColumn(
                    modifier = modifier,
                    verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment,
                    state = scrollState,
                    children = children
                )
            } else {
                LayoutElement.Column(
                    modifier = modifier,
                    verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment,
                    children = children
                )
            }
        }
        is Row -> {
            if (scrollState != null) {
                LayoutElement.ScrollableRow(
                    modifier = modifier,
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = verticalAlignment,
                    state = scrollState,
                    children = children
                )
            } else {
                LayoutElement.Row(
                    modifier = modifier,
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = verticalAlignment,
                    children = children
                )
            }
        }
    }

    data class Box(
        override val modifier: Modifier,
        val contentAlignment: Alignment
    ) : ComposeContainerProjection

    data class Column(
        override val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment,
        val scrollState: ScrollState?
    ) : ComposeContainerProjection

    data class Row(
        override val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment,
        val scrollState: ScrollState?
    ) : ComposeContainerProjection
}

internal fun ComposeTreeNode.toContainerProjectionOrNull(): ComposeContainerProjection? {
    return when (this) {
        is RootNode -> ComposeContainerProjection.Box(
            modifier = modifier,
            contentAlignment = Alignment.TopStart
        )
        is BoxNode -> ComposeContainerProjection.Box(
            modifier = modifier,
            contentAlignment = contentAlignment
        )
        is ColumnNode -> ComposeContainerProjection.Column(
            modifier = modifier,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            scrollState = modifier.verticalScrollState
        )
        is ScrollableColumnNode -> ComposeContainerProjection.Column(
            modifier = modifier,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            scrollState = state
        )
        is RowNode -> ComposeContainerProjection.Row(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            scrollState = modifier.horizontalScrollState
        )
        else -> null
    }
}

