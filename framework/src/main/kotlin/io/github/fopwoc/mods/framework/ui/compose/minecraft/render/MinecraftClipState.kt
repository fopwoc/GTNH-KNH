package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import org.lwjgl.opengl.GL11

internal class MinecraftClipState(
    private val frame: MinecraftRenderFrameContext,
    private val appendInputTarget: (InputTarget) -> Unit,
) {
  private var activeClipRect: Rect? = null
  private val viewportBounds =
      Rect(0, 0, frame.viewportWidth.coerceAtLeast(0), frame.viewportHeight.coerceAtLeast(0))
  // One GL viewport/ScaledResolution query per frame instead of one per clip change.
  private val projection by lazy { resolveMinecraftGuiProjection(frame.client) }

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

    val scissorRect = normalizedRect.toMinecraftScissorRect(projection)
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
