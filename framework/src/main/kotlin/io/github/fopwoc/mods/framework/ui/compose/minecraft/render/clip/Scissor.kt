package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.BufferUtils
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
        if (target.bounds.isEmpty()) {
            return
        }

        val combinedClipRect = normalizeClipRect(mergeClipRects(activeClipRect, target.clipRect))
        if (combinedClipRect?.isEmpty() == true) {
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
        val normalizedRect = normalizeClipRect(rect)
        activeClipRect = normalizedRect
        if (normalizedRect == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            return
        }

        if (normalizedRect.isEmpty()) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(0, 0, 0, 0)
            return
        }

        val scissorRect = normalizedRect.toMinecraftScissorRect(resolveMinecraftGuiProjection(frame.client))
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(scissorRect.x, scissorRect.y, scissorRect.width, scissorRect.height)
    }

    private fun normalizeClipRect(rect: Rect?): Rect? = rect?.intersect(viewportBounds)
}

internal fun mergeClipRects(first: Rect?, second: Rect?): Rect? {
    return when {
        first == null -> second
        second == null -> first
        else -> first.intersect(second)
    }
}

internal fun Rect.toMinecraftScissorRect(
    displayWidth: Int,
    displayHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int
): Rect {
    if (displayWidth <= 0 || displayHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0 || isEmpty()) {
        return Rect(0, 0, 0, 0)
    }

    val left = floor(x * displayWidth.toDouble() / viewportWidth.toDouble()).toInt().coerceIn(0, displayWidth)
    val top = floor(y * displayHeight.toDouble() / viewportHeight.toDouble()).toInt().coerceIn(0, displayHeight)
    val right = ceil((x + width) * displayWidth.toDouble() / viewportWidth.toDouble()).toInt().coerceIn(left, displayWidth)
    val bottom = ceil((y + height) * displayHeight.toDouble() / viewportHeight.toDouble()).toInt().coerceIn(top, displayHeight)
    return Rect(
        x = left,
        y = (displayHeight - bottom).coerceIn(0, displayHeight),
        width = (right - left).coerceAtLeast(0),
        height = (bottom - top).coerceAtLeast(0)
    )
}

internal fun Rect.toMinecraftScissorRect(projection: MinecraftGuiProjection): Rect {
    val scaledWidth = projection.scaledWidth
    val scaledHeight = projection.scaledHeight
    val viewportWidth = projection.viewportWidth
    val viewportHeight = projection.viewportHeight
    if (scaledWidth <= 0.0 || scaledHeight <= 0.0 || viewportWidth <= 0 || viewportHeight <= 0 || isEmpty()) {
        return Rect(0, 0, 0, 0)
    }

    val left = floor(x * viewportWidth.toDouble() / scaledWidth).toInt().coerceIn(0, viewportWidth)
    val top = floor(y * viewportHeight.toDouble() / scaledHeight).toInt().coerceIn(0, viewportHeight)
    val right = ceil((x + width) * viewportWidth.toDouble() / scaledWidth).toInt().coerceIn(left, viewportWidth)
    val bottom = ceil((y + height) * viewportHeight.toDouble() / scaledHeight).toInt().coerceIn(top, viewportHeight)
    return Rect(
        x = projection.viewportX + left,
        y = projection.viewportY + (viewportHeight - bottom).coerceIn(0, viewportHeight),
        width = (right - left).coerceAtLeast(0),
        height = (bottom - top).coerceAtLeast(0)
    )
}

internal data class MinecraftGuiProjection(
    val displayWidth: Int,
    val displayHeight: Int,
    val scaledWidth: Double,
    val scaledHeight: Double,
    val viewportX: Int = 0,
    val viewportY: Int = 0,
    val viewportWidth: Int = displayWidth,
    val viewportHeight: Int = displayHeight,
    val scaleFactor: Int? = null
)

private fun resolveMinecraftGuiProjection(client: Minecraft): MinecraftGuiProjection {
    val displayWidth = client.displayWidth.coerceAtLeast(1)
    val displayHeight = client.displayHeight.coerceAtLeast(1)
    val scaledResolution = ScaledResolution(client, displayWidth, displayHeight)
    val viewportBuffer = BufferUtils.createIntBuffer(16)
    GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer)
    val viewportX = viewportBuffer.get(0)
    val viewportY = viewportBuffer.get(1)
    val viewportWidth = viewportBuffer.get(2).takeIf { it > 0 } ?: displayWidth
    val viewportHeight = viewportBuffer.get(3).takeIf { it > 0 } ?: displayHeight

    return MinecraftGuiProjection(
        displayWidth = displayWidth,
        displayHeight = displayHeight,
        scaledWidth = scaledResolution.scaledWidth_double,
        scaledHeight = scaledResolution.scaledHeight_double,
        viewportX = viewportX,
        viewportY = viewportY,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        scaleFactor = scaledResolution.scaleFactor.takeIf { it > 0 }
    )
}

private fun Rect.isEmpty(): Boolean = width <= 0 || height <= 0

