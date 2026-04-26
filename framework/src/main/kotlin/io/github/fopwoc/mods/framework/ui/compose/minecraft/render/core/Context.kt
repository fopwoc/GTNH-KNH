package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

internal class MinecraftRenderContext(
    private val frame: MinecraftRenderFrameContext,
    appendInputTarget: (InputTarget) -> Unit,
    callbacks: MinecraftPrimitiveRenderCallbacks
) : RenderContext {
    override val viewportWidth: Int
        get() = frame.viewportWidth

    override val viewportHeight: Int
        get() = frame.viewportHeight

    override val mouseX: Int
        get() = frame.mouseX

    override val mouseY: Int
        get() = frame.mouseY

    private val textMetrics = MinecraftFontTextMetrics(frame.font)
    private val primitiveDrawer = MinecraftPrimitiveDrawer(
        font = frame.font,
        callbacks = callbacks
    )
    private val clipState = MinecraftClipState(
        frame = frame,
        appendInputTarget = appendInputTarget
    )

    override val lineHeight: Int
        get() = textMetrics.lineHeight

    override fun textWidth(text: String): Int = textMetrics.textWidth(text)

    override fun wrapText(text: String, maxWidth: Int): List<String> = textMetrics.wrapText(text, maxWidth)

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

    fun resetClipState() {
        clipState.reset()
    }
}

