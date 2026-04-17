package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier(),
    spacing: Int = 4,
    labelOf: (T) -> String = { it.toString() },
    onSelected: (T) -> Unit
) {
    Row(
        modifier = modifier,
        spacing = spacing,
        verticalAlignment = VerticalAlignment.CENTER
    ) {
        options.forEach { option ->
            Button(
                text = labelOf(option),
                modifier = Modifier().width(96),
                enabled = option != selected,
                onClick = {
                    onSelected(option)
                }
            )
        }
    }
}



