package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutProjection
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutShape
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

internal sealed interface ComposeLeafProjection : LayoutProjection {
    override val modifier: Modifier

    override val shape: LayoutShape
        get() = when (this) {
            is ComposeLeafProjection.Text -> LayoutShape.Text(modifier = modifier, text = text, style = style)
            is ComposeLeafProjection.Button -> LayoutShape.Button(modifier = modifier, text = text)
            is ComposeLeafProjection.Checkbox -> LayoutShape.Checkbox(modifier = modifier, label = label)
            is ComposeLeafProjection.TextField -> LayoutShape.TextField(modifier = modifier)
            is ComposeLeafProjection.Slider -> LayoutShape.Slider(modifier = modifier)
            is ComposeLeafProjection.SelectableList -> LayoutShape.SelectableList(
                modifier = modifier,
                items = items,
                rowHeight = rowHeight,
                visibleRowCount = visibleRowCount
            )
            is ComposeLeafProjection.Spacer -> LayoutShape.Spacer(modifier = modifier)
        }

    override fun toLayoutElement(children: List<LayoutElement>): LayoutElement = when (this) {
        is ComposeLeafProjection.Text -> LayoutElement.Text(
            modifier = modifier,
            text = text,
            style = style
        )
        is ComposeLeafProjection.Button -> LayoutElement.Button(
            modifier = modifier,
            hostKey = hostKey,
            text = text,
            enabled = enabled,
            onClick = onClick
        )
        is ComposeLeafProjection.Checkbox -> LayoutElement.Checkbox(
            modifier = modifier,
            hostKey = hostKey,
            label = label,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
        is ComposeLeafProjection.TextField -> LayoutElement.TextField(
            modifier = modifier,
            hostKey = hostKey,
            state = state,
            placeholder = placeholder,
            enabled = enabled,
            style = style
        )
        is ComposeLeafProjection.Slider -> LayoutElement.Slider(
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
        is ComposeLeafProjection.SelectableList -> LayoutElement.SelectableList(
            modifier = modifier,
            hostKey = hostKey,
            items = items,
            selectedIndex = selectedIndex,
            rowHeight = rowHeight,
            visibleRowCount = visibleRowCount,
            onSelectedIndexChange = onSelectedIndexChange
        )
        is ComposeLeafProjection.Spacer -> LayoutElement.Spacer(modifier = modifier)
    }

    data class Text(
        override val modifier: Modifier,
        val text: StyledText,
        val style: TextStyle
    ) : ComposeLeafProjection

    data class Button(
        override val modifier: Modifier,
        val hostKey: HostedWidgetKey,
        val text: StyledText,
        val enabled: Boolean,
        val onClick: () -> Unit
    ) : ComposeLeafProjection

    data class Checkbox(
        override val modifier: Modifier,
        val hostKey: HostedWidgetKey,
        val label: StyledText,
        val checked: Boolean,
        val enabled: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : ComposeLeafProjection

    data class TextField(
        override val modifier: Modifier,
        val hostKey: HostedWidgetKey,
        val state: TextFieldState,
        val placeholder: String,
        val enabled: Boolean,
        val style: TextFieldStyle
    ) : ComposeLeafProjection

    data class Slider(
        override val modifier: Modifier,
        val hostKey: HostedWidgetKey,
        val value: Double,
        val valueRangeStart: Double,
        val valueRangeEnd: Double,
        val label: String,
        val suffix: String,
        val enabled: Boolean,
        val showDecimal: Boolean,
        val onValueChange: (Double) -> Unit
    ) : ComposeLeafProjection

    data class SelectableList(
        override val modifier: Modifier,
        val hostKey: HostedWidgetKey,
        val items: List<String>,
        val selectedIndex: Int,
        val rowHeight: UiUnit,
        val visibleRowCount: Int,
        val onSelectedIndexChange: (Int) -> Unit
    ) : ComposeLeafProjection

    data class Spacer(
        override val modifier: Modifier
    ) : ComposeLeafProjection
}

internal fun ComposeTreeNode.toLeafProjectionOrNull(): ComposeLeafProjection? {
    return when (this) {
        is TextNode -> ComposeLeafProjection.Text(
            modifier = modifier,
            text = text,
            style = style
        )
        is ButtonNode -> ComposeLeafProjection.Button(
            modifier = modifier,
            hostKey = hostKey,
            text = text,
            enabled = enabled,
            onClick = onClick
        )
        is CheckboxNode -> ComposeLeafProjection.Checkbox(
            modifier = modifier,
            hostKey = hostKey,
            label = label,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
        is TextFieldNode -> ComposeLeafProjection.TextField(
            modifier = modifier,
            hostKey = hostKey,
            state = state,
            placeholder = placeholder,
            enabled = enabled,
            style = style
        )
        is SliderNode -> ComposeLeafProjection.Slider(
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
        is SelectableListNode -> ComposeLeafProjection.SelectableList(
            modifier = modifier,
            hostKey = hostKey,
            items = items,
            selectedIndex = selectedIndex,
            rowHeight = rowHeight,
            visibleRowCount = visibleRowCount,
            onSelectedIndexChange = onSelectedIndexChange
        )
        is SpacerNode -> ComposeLeafProjection.Spacer(modifier = modifier)
        else -> null
    }
}


