package io.github.fopwoc.mods.framework.ui.compose.model.element

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

sealed class LayoutElement(open val modifier: Modifier) {
    data class Box(
        override val modifier: Modifier,
        val contentAlignment: Alignment,
        val children: List<LayoutElement>
    ) : LayoutElement(modifier)

    data class Column(
        override val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment,
        val children: List<LayoutElement>
    ) : LayoutElement(modifier)

    data class ScrollableColumn(
        override val modifier: Modifier,
        val verticalArrangement: VerticalArrangement,
        val horizontalAlignment: HorizontalAlignment,
        val state: ScrollState,
        val children: List<LayoutElement>
    ) : LayoutElement(modifier)

    data class Row(
        override val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment,
        val children: List<LayoutElement>
    ) : LayoutElement(modifier)

    data class Text(
        override val modifier: Modifier,
        val text: StyledText,
        val style: TextStyle
    ) : LayoutElement(modifier)

    data class Button(
        override val modifier: Modifier,
        val hostKey: Any,
        val text: StyledText,
        val enabled: Boolean,
        val onClick: () -> Unit
    ) : LayoutElement(modifier)

    data class Checkbox(
        override val modifier: Modifier,
        val hostKey: Any,
        val label: StyledText,
        val checked: Boolean,
        val enabled: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : LayoutElement(modifier)

    data class TextField(
        override val modifier: Modifier,
        val hostKey: Any,
        val state: TextFieldState,
        val placeholder: String,
        val enabled: Boolean,
        val style: TextFieldStyle
    ) : LayoutElement(modifier)

    data class Slider(
        override val modifier: Modifier,
        val hostKey: Any,
        val value: Double,
        val valueRangeStart: Double,
        val valueRangeEnd: Double,
        val label: String,
        val suffix: String,
        val enabled: Boolean,
        val showDecimal: Boolean,
        val onValueChange: (Double) -> Unit
    ) : LayoutElement(modifier)

    data class SelectableList(
        override val modifier: Modifier,
        val hostKey: Any,
        val items: List<String>,
        val selectedIndex: Int,
        val rowHeight: UiUnit,
        val visibleRowCount: Int,
        val onSelectedIndexChange: (Int) -> Unit
    ) : LayoutElement(modifier)

    data class Spacer(
        override val modifier: Modifier
    ) : LayoutElement(modifier)
}


