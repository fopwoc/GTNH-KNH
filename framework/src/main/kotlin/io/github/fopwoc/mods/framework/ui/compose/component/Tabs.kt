package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

object TabsDefaults {
    val Spacing: UiUnit = UiTokens.MediumGap
}

@Composable
fun <T> Tabs(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier(),
    spacing: UiUnit = TabsDefaults.Spacing,
    labelOf: (T) -> String = { it.toString() },
    onSelected: (T) -> Unit,
    content: @Composable (T) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = VerticalArrangement.spacedBy(spacing),
        horizontalAlignment = HorizontalAlignment.START
    ) {
        SegmentedControl(
            options = options,
            selected = selected,
            modifier = Modifier().fillMaxWidth(),
            labelOf = labelOf,
            onSelected = onSelected
        )
        content(selected)
    }
}



