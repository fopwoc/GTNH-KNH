package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

object PanelDefaults {
    val BackgroundColor: Color = Color(0xB0141418)
    val BorderColor: Color = Color(0xFF4A4A56)
    val ContentPadding: UiUnit = UiTokens.PanelPadding
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = PanelDefaults.BackgroundColor,
    borderColor: Color = PanelDefaults.BorderColor,
    contentPadding: UiUnit = PanelDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(borderColor)
    ) {
        Box(
            modifier = Modifier
                .padding(contentPadding)
        ) {
            content()
        }
    }
}



