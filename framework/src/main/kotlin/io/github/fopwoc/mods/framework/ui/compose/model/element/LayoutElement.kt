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
        val scrollValue: Int = state.value,
        val children: List<LayoutElement>
    ) : LayoutElement(modifier)

    data class ScrollableRow(
        override val modifier: Modifier,
        val horizontalArrangement: HorizontalArrangement,
        val verticalAlignment: VerticalAlignment,
        val state: ScrollState,
        val scrollValue: Int = state.value,
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

    class Button(
        override val modifier: Modifier,
        val hostKey: Any,
        val text: StyledText,
        val enabled: Boolean,
        val onClick: () -> Unit
    ) : LayoutElement(modifier) {
        override fun equals(other: Any?): Boolean {
            return other is Button &&
                modifier == other.modifier &&
                hostKey == other.hostKey &&
                text == other.text &&
                enabled == other.enabled
        }

        override fun hashCode(): Int {
            var result = modifier.hashCode()
            result = 31 * result + hostKey.hashCode()
            result = 31 * result + text.hashCode()
            result = 31 * result + enabled.hashCode()
            return result
        }
    }

    class Checkbox(
        override val modifier: Modifier,
        val hostKey: Any,
        val label: StyledText,
        val checked: Boolean,
        val enabled: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : LayoutElement(modifier) {
        override fun equals(other: Any?): Boolean {
            return other is Checkbox &&
                modifier == other.modifier &&
                hostKey == other.hostKey &&
                label == other.label &&
                checked == other.checked &&
                enabled == other.enabled
        }

        override fun hashCode(): Int {
            var result = modifier.hashCode()
            result = 31 * result + hostKey.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + checked.hashCode()
            result = 31 * result + enabled.hashCode()
            return result
        }
    }

    data class TextField(
        override val modifier: Modifier,
        val hostKey: Any,
        val state: TextFieldState,
        val placeholder: String,
        val enabled: Boolean,
        val style: TextFieldStyle
    ) : LayoutElement(modifier)

    class Slider(
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
    ) : LayoutElement(modifier) {
        override fun equals(other: Any?): Boolean {
            return other is Slider &&
                modifier == other.modifier &&
                hostKey == other.hostKey &&
                value == other.value &&
                valueRangeStart == other.valueRangeStart &&
                valueRangeEnd == other.valueRangeEnd &&
                label == other.label &&
                suffix == other.suffix &&
                enabled == other.enabled &&
                showDecimal == other.showDecimal
        }

        override fun hashCode(): Int {
            var result = modifier.hashCode()
            result = 31 * result + hostKey.hashCode()
            result = 31 * result + value.hashCode()
            result = 31 * result + valueRangeStart.hashCode()
            result = 31 * result + valueRangeEnd.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + suffix.hashCode()
            result = 31 * result + enabled.hashCode()
            result = 31 * result + showDecimal.hashCode()
            return result
        }
    }

    class SelectableList(
        override val modifier: Modifier,
        val hostKey: Any,
        val items: List<String>,
        val selectedIndex: Int,
        val rowHeight: UiUnit,
        val visibleRowCount: Int,
        val onSelectedIndexChange: (Int) -> Unit
    ) : LayoutElement(modifier) {
        override fun equals(other: Any?): Boolean {
            return other is SelectableList &&
                modifier == other.modifier &&
                hostKey == other.hostKey &&
                items == other.items &&
                selectedIndex == other.selectedIndex &&
                rowHeight == other.rowHeight &&
                visibleRowCount == other.visibleRowCount
        }

        override fun hashCode(): Int {
            var result = modifier.hashCode()
            result = 31 * result + hostKey.hashCode()
            result = 31 * result + items.hashCode()
            result = 31 * result + selectedIndex
            result = 31 * result + rowHeight.hashCode()
            result = 31 * result + visibleRowCount
            return result
        }
    }

    data class Spacer(
        override val modifier: Modifier
    ) : LayoutElement(modifier)
}



