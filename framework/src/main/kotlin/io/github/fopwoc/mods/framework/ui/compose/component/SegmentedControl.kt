package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    spacing: UiUnit = SegmentedControlDefaults.Spacing,
    labelOf: (T) -> String = { it.toString() },
    selectedLabelOf: (String) -> StyledText = SegmentedControlDefaults::selectedLabel,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = HorizontalArrangement.spacedBy(spacing),
        verticalAlignment = VerticalAlignment.CENTER,
    ) {
        options.forEach { option ->
            val label = labelOf(option)
            Button(
                text = if (option == selected) selectedLabelOf(label) else StyledText.of(label),
                modifier = Modifier.width(SegmentedControlDefaults.ButtonWidth),
                onClick = {
                    if (option != selected) {
                        onSelected(option)
                    }
                },
            )
        }
    }
}
