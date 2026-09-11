package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import org.lwjgl.input.Mouse

internal data class MouseWheelEvent(
    val wheelDelta: Int,
    val eventX: Int,
    val eventY: Int,
)

internal data class ResolvedMouseWheelEvent(
    val wheelDelta: Int,
    val mouseX: Int,
    val mouseY: Int,
)

internal interface MouseEventReader {
  fun readWheelEvent(): MouseWheelEvent
}

internal object LwjglMouseEventReader : MouseEventReader {
  override fun readWheelEvent(): MouseWheelEvent {
    return MouseWheelEvent(
        wheelDelta = Mouse.getEventDWheel(),
        eventX = Mouse.getEventX(),
        eventY = Mouse.getEventY(),
    )
  }
}

internal fun resolveMouseWheelEvent(
    width: Int,
    height: Int,
    displayWidth: Int?,
    displayHeight: Int?,
    event: MouseWheelEvent,
): ResolvedMouseWheelEvent? {
  if (event.wheelDelta == 0 || width <= 0 || height <= 0) {
    return null
  }

  val resolvedDisplayWidth = displayWidth ?: return null
  val resolvedDisplayHeight = displayHeight ?: return null
  if (resolvedDisplayWidth <= 0 || resolvedDisplayHeight <= 0) {
    return null
  }

  return ResolvedMouseWheelEvent(
      wheelDelta = event.wheelDelta,
      mouseX = event.eventX * width / resolvedDisplayWidth,
      mouseY = height - event.eventY * height / resolvedDisplayHeight - 1,
  )
}
