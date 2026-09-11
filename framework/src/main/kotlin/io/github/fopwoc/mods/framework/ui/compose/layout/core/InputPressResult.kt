package io.github.fopwoc.mods.framework.ui.compose.layout.core

internal data class InputPressResult(
    val consumed: Boolean,
    val session: ActivePointerSession? = null,
) {
  companion object {
    val Ignored = InputPressResult(consumed = false)
    val Consumed = InputPressResult(consumed = true)

    fun captured(session: ActivePointerSession): InputPressResult {
      return InputPressResult(consumed = true, session = session)
    }
  }
}
