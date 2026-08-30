package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import cpw.mods.fml.client.config.GuiCheckBox

internal class HostedCheckbox(
    val widget: GuiCheckBox,
    var onCheckedChange: (Boolean) -> Unit,
) : HostedWidget {
  override var lastSeenEpoch: Int = -1
}
