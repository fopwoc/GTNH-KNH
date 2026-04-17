package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.gui.GuiTextField

internal class HostedTextField(
    val state: TextFieldState,
    val widget: GuiTextField
) {
    var lastSeenEpoch: Int = -1
    var clipRect: Rect? = null
}

