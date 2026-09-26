/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.ClipStack
import io.github.fopwoc.mods.framework.ui.compose.layout.render.GpuCanvasCache
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextWrapCache
import io.github.fopwoc.mods.framework.ui.compose.layout.render.Widget
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.text.FormattedTextWrap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents

/**
 * Draws a composed frame through the GUI render state of Minecraft 26.x, so it runs on whichever
 * graphics backend (OpenGL or Vulkan) the game uses. Clips map to the extractor's scissor stack.
 */
internal class ModernRenderContext(
    private val graphics: GuiGraphicsExtractor,
    private val font: Font,
    override val viewportWidth: Int,
    override val viewportHeight: Int,
    override val mouseX: Int,
    override val mouseY: Int,
    appendInputTarget: (InputTarget) -> Unit,
    private val gpuCanvas: GpuCanvasCache<ModernGpuImageAtlas>,
    private val wrapCache: TextWrapCache,
    override val textFields: TextFieldHost,
) : RenderContext {
    private var scissorPushed = false
    private val clips =
        ClipStack(viewportWidth, viewportHeight, appendInputTarget) { rect ->
            if (scissorPushed) {
                graphics.disableScissor()
                scissorPushed = false
            }
            if (rect != null) {
                graphics.enableScissor(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
                scissorPushed = true
            }
        }

    override val lineHeight: Int
        get() = font.lineHeight

    override fun textWidth(text: String): Int = font.width(text)

    override fun trimToWidth(text: String, maxWidth: Int, fromEnd: Boolean): String =
        font.plainSubstrByWidth(text, maxWidth, fromEnd)

    override fun wrapText(text: String, maxWidth: Int): List<String> {
        if (maxWidth <= 0) return listOf(text)
        return wrapCache.getOrPut(text, maxWidth) {
            FormattedTextWrap.wrap(text, maxWidth, font::width)
        }
    }

    override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) =
        graphics.fill(left, top, right, bottom, color.argbInt)

    override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) =
        graphics.horizontalLine(startX, endX, y, color.argbInt)

    override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) =
        graphics.verticalLine(x, startY, endY, color.argbInt)

    override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) =
        graphics.text(font, text, x, y, color.textArgb, shadow)

    override fun registerInputTarget(target: InputTarget) = clips.registerInputTarget(target)

    override fun withClipRect(rect: Rect, block: () -> Unit) = clips.withClipRect(rect, block)

    override fun drawGpuCanvas(bounds: Rect, frame: GpuCanvasFrame, handle: Any) =
        clips.withClipRect(bounds) { gpuCanvas.renderer(handle).draw(graphics, bounds, frame) }

    override fun drawWidget(widget: Widget, x: Int, y: Int, width: Int, height: Int) =
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, widget.sprite, x, y, width, height)

    override fun playClickSound() {
        Minecraft.getInstance()
            .soundManager
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
    }

    override fun keyModifiers(): KeyModifiers =
        Minecraft.getInstance().let {
            KeyModifiers(
                ctrl = it.hasControlDown(),
                shift = it.hasShiftDown(),
                alt = it.hasAltDown(),
            )
        }

    fun resetClipState() = clips.reset()
}

/** Like 1.7.10 fonts, a colour without alpha means opaque; 26.x would otherwise draw nothing. */
private val Color.textArgb: Int
    get() = argbInt.let { if (it ushr 26 == 0) it or 0xFF000000.toInt() else it }

private val Widget.sprite: Identifier
    get() =
        Identifier.withDefaultNamespace(
            when (this) {
                Widget.Button,
                Widget.CheckboxBox -> "widget/button"
                Widget.ButtonHovered -> "widget/button_highlighted"
                Widget.ButtonDisabled -> "widget/button_disabled"
                Widget.SliderTrack -> "widget/slider"
                Widget.SliderKnob -> "widget/slider_handle"
                Widget.SliderKnobHovered -> "widget/slider_handle_highlighted"
            }
        )
/*?}*/
