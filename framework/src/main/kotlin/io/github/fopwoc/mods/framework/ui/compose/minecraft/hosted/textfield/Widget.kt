package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.gui.GuiTextField

internal class HostedTextField(
    val hostKey: HostedWidgetKey,
    val state: TextFieldState,
    val widget: GuiTextField,
) : HostedWidget {
  var currentState: TextFieldState = state
  override var lastSeenEpoch: Int = -1
}
