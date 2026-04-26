package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

internal interface MinecraftPrimitiveRenderCallbacks {
    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int)

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int)

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int)
}
