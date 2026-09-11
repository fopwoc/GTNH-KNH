package io.github.fopwoc.mods.framework.ui.compose.layout.core

internal class ActivePointerSession(
    val button: Int,
    private val validityCheck: () -> Boolean = { true },
    private val onDragHandler: (mouseX: Int, mouseY: Int) -> Boolean = { _, _ -> false },
    private val onReleaseHandler: (mouseX: Int, mouseY: Int, button: Int) -> Boolean = { _, _, _ ->
      false
    },
) {
  fun isValid(): Boolean = validityCheck()

  fun onDrag(mouseX: Int, mouseY: Int): Boolean = onDragHandler(mouseX, mouseY)

  fun onRelease(mouseX: Int, mouseY: Int, button: Int): Boolean =
      onReleaseHandler(mouseX, mouseY, button)
}
