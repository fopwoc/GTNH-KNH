package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

object PanelDefaults {
    const val BackgroundColor: Int = 0xB0141418.toInt()
    const val BorderColor: Int = 0xFF4A4A56.toInt()
    val ContentPadding: UiUnit = UiTokens.PanelPadding
}

@Composable
fun Panel(
    modifier: Modifier = Modifier(),
    backgroundColor: Int = PanelDefaults.BackgroundColor,
    borderColor: Int = PanelDefaults.BorderColor,
    contentPadding: UiUnit = PanelDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(borderColor)
    ) {
        Box(
            modifier = Modifier()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}



