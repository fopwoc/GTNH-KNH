package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

internal interface ComposeGuiScreenRenderCallbacks : MinecraftPrimitiveRenderCallbacks {
  fun drawBackground()

  fun drawTooltip(lines: List<String>, x: Int, y: Int)

  fun drawFallback(mouseX: Int, mouseY: Int, partialTicks: Float)
}
