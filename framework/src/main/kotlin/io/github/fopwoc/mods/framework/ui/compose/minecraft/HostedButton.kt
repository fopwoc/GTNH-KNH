package io.github.fopwoc.mods.framework.ui.compose.minecraft

import cpw.mods.fml.client.config.GuiButtonExt
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect

internal class HostedButton(
    val widget: GuiButtonExt,
    var onClick: () -> Unit
) {
    var lastSeenEpoch: Int = -1
    var clipRect: Rect? = null
}

