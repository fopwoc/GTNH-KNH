package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11

internal fun Rect.toMinecraftScissorRect(projection: MinecraftGuiProjection): Rect {
  val scaledWidth = projection.scaledWidth
  val scaledHeight = projection.scaledHeight
  val viewportWidth = projection.viewportWidth
  val viewportHeight = projection.viewportHeight
  if (
      scaledWidth <= 0.0 ||
          scaledHeight <= 0.0 ||
          viewportWidth <= 0 ||
          viewportHeight <= 0 ||
          isEmpty()
  ) {
    return Rect(0, 0, 0, 0)
  }

  val left = floor(x * viewportWidth.toDouble() / scaledWidth).toInt().coerceIn(0, viewportWidth)
  val top = floor(y * viewportHeight.toDouble() / scaledHeight).toInt().coerceIn(0, viewportHeight)
  val right =
      ceil((x + width) * viewportWidth.toDouble() / scaledWidth)
          .toInt()
          .coerceIn(left, viewportWidth)
  val bottom =
      ceil((y + height) * viewportHeight.toDouble() / scaledHeight)
          .toInt()
          .coerceIn(top, viewportHeight)
  return Rect(
      x = projection.viewportX + left,
      y = projection.viewportY + (viewportHeight - bottom).coerceIn(0, viewportHeight),
      width = (right - left).coerceAtLeast(0),
      height = (bottom - top).coerceAtLeast(0),
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
    val scaleFactor: Int? = null,
)

// Direct buffers are expensive to allocate and only freed by GC; GUI rendering is single-threaded.
// Kept in its own holder so the pure helpers in this file stay usable without LWJGL on the path.
private object ViewportQuery {
  private val buffer = BufferUtils.createIntBuffer(16)

  fun read(): IntArray {
    buffer.clear()
    GL11.glGetInteger(GL11.GL_VIEWPORT, buffer)
    return IntArray(4) { buffer.get(it) }
  }
}

internal fun resolveMinecraftGuiProjection(client: Minecraft): MinecraftGuiProjection {
  val displayWidth = client.displayWidth.coerceAtLeast(1)
  val displayHeight = client.displayHeight.coerceAtLeast(1)
  val scaledResolution = ScaledResolution(client, displayWidth, displayHeight)
  val viewportBuffer = ViewportQuery.read()
  val viewportX = viewportBuffer[0]
  val viewportY = viewportBuffer[1]
  val viewportWidth = viewportBuffer[2].takeIf { it > 0 } ?: displayWidth
  val viewportHeight = viewportBuffer[3].takeIf { it > 0 } ?: displayHeight

  return MinecraftGuiProjection(
      displayWidth = displayWidth,
      displayHeight = displayHeight,
      scaledWidth = scaledResolution.scaledWidth_double,
      scaledHeight = scaledResolution.scaledHeight_double,
      viewportX = viewportX,
      viewportY = viewportY,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
      scaleFactor = scaledResolution.scaleFactor.takeIf { it > 0 },
  )
}

internal fun Rect.isEmpty(): Boolean = width <= 0 || height <= 0
