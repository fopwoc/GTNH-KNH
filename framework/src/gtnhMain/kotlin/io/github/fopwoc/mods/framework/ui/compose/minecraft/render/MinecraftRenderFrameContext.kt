package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer

internal data class MinecraftRenderFrameContext(
    val client: Minecraft,
    val font: FontRenderer,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val mouseX: Int,
    val mouseY: Int,
    val renderEpoch: Int,
)
