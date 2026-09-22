package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextWrapCache

import cpw.mods.fml.client.config.GuiUtils
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.layout.render.Widget
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiScreen
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

internal class MinecraftRenderContext(
    private val frame: MinecraftRenderFrameContext,
    appendInputTarget: (InputTarget) -> Unit,
    callbacks: MinecraftPrimitiveRenderCallbacks,
    private val gpuCanvas: GpuCanvasRenderer = GpuCanvasRenderer(),
    wrapCache: TextWrapCache = TextWrapCache(),
    override val textFields: TextFieldHost = TextFieldHost.None,
) : RenderContext {
    override val viewportWidth: Int
        get() = frame.viewportWidth

    override val viewportHeight: Int
        get() = frame.viewportHeight

    override val mouseX: Int
        get() = frame.mouseX

    override val mouseY: Int
        get() = frame.mouseY

    private val textMetrics = MinecraftFontTextMetrics(frame.font, wrapCache)
    private val primitiveDrawer =
        MinecraftPrimitiveDrawer(
            font = frame.font,
            callbacks = callbacks,
        )
    private val clipState = minecraftClipState(frame, appendInputTarget)

    override val lineHeight: Int
        get() = textMetrics.lineHeight

    override fun textWidth(text: String): Int = textMetrics.textWidth(text)

    override fun wrapText(text: String, maxWidth: Int): List<String> =
        textMetrics.wrapText(text, maxWidth)

    override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) {
        primitiveDrawer.fillRect(left, top, right, bottom, color)
    }

    override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) {
        primitiveDrawer.drawHorizontalLine(startX, endX, y, color)
    }

    override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) {
        primitiveDrawer.drawVerticalLine(x, startY, endY, color)
    }

    override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) {
        primitiveDrawer.drawText(text, x, y, color, shadow)
    }

    override fun registerInputTarget(target: InputTarget) {
        clipState.registerInputTarget(target)
    }

    override fun withClipRect(rect: Rect, block: () -> Unit) {
        clipState.withClipRect(rect, block)
    }

    override fun drawGpuCanvas(bounds: Rect, frame: GpuCanvasFrame, handle: Any) {
        clipState.withClipRect(bounds) {
            gpuCanvas.draw(bounds, viewportWidth, viewportHeight, frame, handle)
        }
    }

    override fun drawWidget(widget: Widget, x: Int, y: Int, width: Int, height: Int) {
        GL11.glColor4f(1f, 1f, 1f, 1f)
        when (widget) {
            Widget.Button, Widget.CheckboxBox -> drawSlice(BUTTON, x, y, width, height)
            Widget.ButtonHovered -> drawSlice(BUTTON_HOVERED, x, y, width, height)
            Widget.ButtonDisabled, Widget.SliderTrack -> drawSlice(BUTTON_DISABLED, x, y, width, height)
            // The 1.7.10 knob is the two outer 4 px strips of a button face.
            Widget.SliderKnob, Widget.SliderKnobHovered -> {
                val v = if (widget == Widget.SliderKnobHovered) BUTTON_HOVERED.v else BUTTON.v
                val half = width / 2
                frame.client.textureManager.bindTexture(WIDGETS_TEXTURE)
                spriteGui.drawTexturedModalRect(x, y, 0, v, half, height)
                spriteGui.drawTexturedModalRect(x + half, y, BUTTON.width - (width - half), v, width - half, height)
            }
        }
    }

    private fun drawSlice(slice: WidgetSlice, x: Int, y: Int, width: Int, height: Int) {
        GuiUtils.drawContinuousTexturedBox(
            WIDGETS_TEXTURE,
            x,
            y,
            slice.u,
            slice.v,
            width,
            height,
            slice.width,
            slice.height,
            slice.top,
            slice.bottom,
            slice.left,
            slice.right,
            0f,
        )
    }

    override fun playClickSound() {
        frame.client.soundHandler.playSound(
            PositionedSoundRecord.func_147674_a(ResourceLocation("gui.button.press"), 1.0f)
        )
    }

    override fun keyModifiers(): KeyModifiers =
        KeyModifiers(ctrl = GuiScreen.isCtrlKeyDown(), shift = GuiScreen.isShiftKeyDown())

    fun resetClipState() {
        clipState.reset()
    }
}

private val WIDGETS_TEXTURE = ResourceLocation("textures/gui/widgets.png")
private val spriteGui = Gui()

/** A 9-slice source rectangle on `textures/gui/widgets.png` with its stretch borders. */
private data class WidgetSlice(
    val u: Int,
    val v: Int,
    val width: Int,
    val height: Int,
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
)

private val BUTTON_DISABLED = WidgetSlice(u = 0, v = 46, width = 200, height = 20, top = 2, bottom = 3, left = 2, right = 2)
private val BUTTON = BUTTON_DISABLED.copy(v = 66)
private val BUTTON_HOVERED = BUTTON_DISABLED.copy(v = 86)
