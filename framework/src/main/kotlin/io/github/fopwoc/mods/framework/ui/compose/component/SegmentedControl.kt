package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

object SegmentedControlDefaults {
    val Spacing: UiUnit = UiTokens.SmallGap
    val ButtonWidth: UiUnit = UiTokens.StandardButtonWidth
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier(),
    spacing: UiUnit = SegmentedControlDefaults.Spacing,
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
                modifier = Modifier().width(SegmentedControlDefaults.ButtonWidth),
                enabled = option != selected,
                onClick = {
                    onSelected(option)
                }
            )
        }
    }
}



