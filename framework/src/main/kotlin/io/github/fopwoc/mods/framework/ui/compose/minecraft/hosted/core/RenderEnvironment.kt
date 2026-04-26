package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftRenderFrameContext
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer

internal data class MinecraftHostedWidgetRenderEnvironment(
    val frame: MinecraftRenderFrameContext,
    val registerInputTarget: (InputTarget) -> Unit,
    val focusTextField: (TextFieldState) -> Unit
) {
    val client: Minecraft
        get() = frame.client

    val font: FontRenderer
        get() = frame.font

    val mouseX: Int
        get() = frame.mouseX

    val mouseY: Int
        get() = frame.mouseY

    val renderEpoch: Int
        get() = frame.renderEpoch
}

