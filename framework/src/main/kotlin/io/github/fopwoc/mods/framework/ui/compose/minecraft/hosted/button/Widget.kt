package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import cpw.mods.fml.client.config.GuiButtonExt

internal class HostedButton(
    val widget: GuiButtonExt,
    var onClick: () -> Unit
) : HostedWidget {
    override var lastSeenEpoch: Int = -1
}


