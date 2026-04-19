package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

@Composable
fun ToggleButton(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Button(
        text = "$label: ${if (checked) "ON" else "OFF"}",
        modifier = modifier,
        enabled = enabled,
        onClick = {
            onCheckedChange(!checked)
        }
    )
}



