package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import org.lwjgl.opengl.GL11
import kotlin.math.ceil
import kotlin.math.floor

internal class MinecraftClipState(
    private val frame: MinecraftRenderFrameContext,
    private val appendInputTarget: (InputTarget) -> Unit
) {
    private var activeClipRect: Rect? = null
    private val viewportBounds = Rect(0, 0, frame.viewportWidth.coerceAtLeast(0), frame.viewportHeight.coerceAtLeast(0))

    fun registerInputTarget(target: InputTarget) {
        if (target.bounds.width <= 0 || target.bounds.height <= 0) {
            return
        }

        val combinedClipRect = mergeClipRects(activeClipRect, target.clipRect)
        if (combinedClipRect != null && (combinedClipRect.width <= 0 || combinedClipRect.height <= 0)) {
            return
        }

        appendInputTarget(target.copy(clipRect = combinedClipRect))
    }

    fun withClipRect(rect: Rect, block: () -> Unit) {
        val previousClipRect = activeClipRect
        val nextClipRect = mergeClipRects(previousClipRect, rect)
        applyClipRect(nextClipRect)
        try {
            block()
        } finally {
            applyClipRect(previousClipRect)
        }
    }

    fun reset() {
        applyClipRect(null)
    }

    private fun applyClipRect(rect: Rect?) {
        val normalizedRect = rect?.intersect(viewportBounds)
        activeClipRect = normalizedRect
        if (normalizedRect == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            return
        }

        if (normalizedRect.width <= 0 || normalizedRect.height <= 0) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(0, 0, 0, 0)
            return
        }

        val scissorRect = normalizedRect.toScissorRect(
            displayWidth = frame.client.displayWidth.coerceAtLeast(1),
            displayHeight = frame.client.displayHeight.coerceAtLeast(1),
            viewportWidth = viewportBounds.width.coerceAtLeast(1),
            viewportHeight = viewportBounds.height.coerceAtLeast(1)
        )
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(scissorRect.x, scissorRect.y, scissorRect.width, scissorRect.height)
    }
}

internal fun mergeClipRects(first: Rect?, second: Rect?): Rect? {
    return when {
        first == null -> second
        second == null -> first
        else -> first.intersect(second)
    }
}

private fun Rect.toScissorRect(
    displayWidth: Int,
    displayHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int
): Rect {
    val scaleX = displayWidth.toDouble() / viewportWidth.toDouble()
    val scaleY = displayHeight.toDouble() / viewportHeight.toDouble()
    val left = floor(x * scaleX).toInt().coerceIn(0, displayWidth)
    val top = floor(y * scaleY).toInt().coerceIn(0, displayHeight)
    val right = ceil((x + width) * scaleX).toInt().coerceIn(left, displayWidth)
    val bottom = ceil((y + height) * scaleY).toInt().coerceIn(top, displayHeight)
    return Rect(
        x = left,
        y = (displayHeight - bottom).coerceIn(0, displayHeight),
        width = (right - left).coerceAtLeast(0),
        height = (bottom - top).coerceAtLeast(0)
    )
}
