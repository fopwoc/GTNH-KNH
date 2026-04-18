package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.gui.GuiTextField

internal class HostedTextField(
    val hostKey: Any,
    val state: TextFieldState,
    val widget: GuiTextField
) {
    var currentState: TextFieldState = state
    var lastSeenEpoch: Int = -1
}

