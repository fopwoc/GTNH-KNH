package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextWrapCache

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.RenderSurface
import net.minecraft.client.Minecraft

/** GL11 drawing of 1.7.10 GUIs; [callbacks] are the owning screen's or HUD's rect primitives. */
@SideOnly(Side.CLIENT)
internal class GtnhRenderSurface(private val callbacks: MinecraftPrimitiveRenderCallbacks) : RenderSurface {
    private val gpuCanvas = GpuCanvasRenderer()
    private val wrapCache = TextWrapCache()
    private var context: MinecraftRenderContext? = null
    private var renderEpoch = 0

    override fun beginFrame(
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        inputTargets: MutableList<InputTarget>,
        textFields: TextFieldHost,
    ): RenderContext {
        val client = Minecraft.getMinecraft()
        renderEpoch += 1
        val frame = MinecraftRenderFrameContext(client, client.fontRenderer, width, height, mouseX, mouseY, renderEpoch)
        gpuCanvas.beginFrame()
        return MinecraftRenderContext(
            frame = frame,
            appendInputTarget = inputTargets::add,
            callbacks = callbacks,
            gpuCanvas = gpuCanvas,
            wrapCache = wrapCache,
            textFields = textFields,
        ).also { context = it }
    }

    override fun endFrame() {
        context?.resetClipState()
        context = null
        gpuCanvas.endFrame()
    }

    override fun dispose() = gpuCanvas.dispose()
}
