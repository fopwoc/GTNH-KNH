package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

@Composable
fun <T> Tabs(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier(),
    labelOf: (T) -> String = { it.toString() },
    onSelected: (T) -> Unit,
    content: @Composable (T) -> Unit
) {
    Column(
        modifier = modifier,
        spacing = 6,
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



