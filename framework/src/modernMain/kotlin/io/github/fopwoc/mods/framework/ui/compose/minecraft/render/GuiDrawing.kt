package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

// What a screen or HUD layer draws with (26.x extracts GUI render state instead of drawing), and
// the GPU canvas atlas that draws through it.
/*? if >=26 {*/
internal typealias GuiDrawing = net.minecraft.client.gui.GuiGraphicsExtractor

internal typealias GpuImageAtlas = ModernGpuImageAtlas
/*?} else {*/
/*internal typealias GuiDrawing = net.minecraft.client.gui.GuiGraphics

internal typealias GpuImageAtlas = LegacyGpuImageAtlas
*//*?}*/
