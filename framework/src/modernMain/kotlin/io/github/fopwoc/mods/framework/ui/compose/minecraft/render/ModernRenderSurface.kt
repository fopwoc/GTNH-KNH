/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.render.GpuCanvasCache
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextWrapCache
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.RenderSurface
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** A [RenderSurface] over the GUI extractor the game hands a screen or HUD layer each frame. */
internal class ModernRenderSurface : RenderSurface {
    private val gpuCanvas = GpuCanvasCache(::ModernGpuImageAtlas, ModernGpuImageAtlas::dispose)
    private val wrapCache = TextWrapCache()
    private var graphics: GuiGraphicsExtractor? = null
    private var context: ModernRenderContext? = null

    /** Makes [graphics] the target of the frames drawn inside [block]. */
    fun <T> drawInto(graphics: GuiGraphicsExtractor, block: () -> T): T {
        this.graphics = graphics
        try {
            return block()
        } finally {
            this.graphics = null
        }
    }

    override fun beginFrame(
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        inputTargets: MutableList<InputTarget>,
        textFields: TextFieldHost,
    ): RenderContext {
        val target = checkNotNull(graphics) { "ModernRenderSurface drawn outside drawInto" }
        gpuCanvas.beginFrame()
        return ModernRenderContext(
                graphics = target,
                font = Minecraft.getInstance().font,
                viewportWidth = width,
                viewportHeight = height,
                mouseX = mouseX,
                mouseY = mouseY,
                appendInputTarget = inputTargets::add,
                gpuCanvas = gpuCanvas,
                wrapCache = wrapCache,
                textFields = textFields,
            )
            .also { context = it }
    }

    override fun endFrame() {
        context?.resetClipState()
        context = null
        gpuCanvas.endFrame()
    }

    override fun dispose() = gpuCanvas.dispose()
}
/*?}*/
