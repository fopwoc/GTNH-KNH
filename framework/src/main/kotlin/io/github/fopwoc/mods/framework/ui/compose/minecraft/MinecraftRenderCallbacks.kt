package io.github.fopwoc.mods.framework.ui.compose.minecraft

internal interface MinecraftPrimitiveRenderCallbacks {
    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int)

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int)

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int)
}

internal interface ComposeGuiScreenRenderCallbacks : MinecraftPrimitiveRenderCallbacks {
    fun drawBackground()

    fun drawTooltip(lines: List<String>, x: Int, y: Int)

    fun drawFallback(mouseX: Int, mouseY: Int, partialTicks: Float)
}

