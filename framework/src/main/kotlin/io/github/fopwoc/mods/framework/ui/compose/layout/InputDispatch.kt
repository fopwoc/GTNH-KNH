package io.github.fopwoc.mods.framework.ui.compose.layout

enum class InputTargetKind {
    BUTTON,
    CHECKBOX,
    SLIDER,
    SELECTABLE_LIST,
    TEXT_FIELD,
    SCROLL_WHEEL,
    SCROLL_THUMB
}

data class InputTarget(
    val kind: InputTargetKind,
    val bounds: Rect,
    val clipRect: Rect? = null,
    val onPress: ((mouseX: Int, mouseY: Int, button: Int) -> InputPressResult)? = null,
    val onWheel: ((mouseX: Int, mouseY: Int, wheelDelta: Int) -> Boolean)? = null
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean {
        return bounds.contains(mouseX, mouseY) && clipRect?.contains(mouseX, mouseY) != false
    }
}

data class InputPressResult(
    val consumed: Boolean,
    val session: ActivePointerSession? = null
) {
    companion object {
        val Ignored = InputPressResult(consumed = false)
        val Consumed = InputPressResult(consumed = true)

        fun captured(session: ActivePointerSession): InputPressResult {
            return InputPressResult(consumed = true, session = session)
        }
    }
}

class ActivePointerSession(
    val button: Int,
    private val validityCheck: () -> Boolean = { true },
    private val onDragHandler: (mouseX: Int, mouseY: Int) -> Boolean = { _, _ -> false },
    private val onReleaseHandler: (mouseX: Int, mouseY: Int, button: Int) -> Boolean = { _, _, _ -> false }
) {
    fun isValid(): Boolean = validityCheck()

    fun onDrag(mouseX: Int, mouseY: Int): Boolean = onDragHandler(mouseX, mouseY)

    fun onRelease(mouseX: Int, mouseY: Int, button: Int): Boolean = onReleaseHandler(mouseX, mouseY, button)
}

object InputDispatcher {
    fun findTopmostPressTarget(targets: List<InputTarget>, mouseX: Int, mouseY: Int): InputTarget? {
        return findTopmostTarget(targets, mouseX, mouseY) { it.onPress != null }
    }

    fun findTopmostWheelTarget(targets: List<InputTarget>, mouseX: Int, mouseY: Int): InputTarget? {
        return findTopmostTarget(targets, mouseX, mouseY) { it.onWheel != null }
    }

    fun shouldBlurFocusedTextFieldAfterPress(
        mouseButton: Int,
        target: InputTarget?,
        pressResult: InputPressResult
    ): Boolean {
        if (mouseButton != 0) {
            return false
        }

        return when {
            target == null -> true
            target.kind == InputTargetKind.TEXT_FIELD -> false
            else -> pressResult.consumed
        }
    }

    private inline fun findTopmostTarget(
        targets: List<InputTarget>,
        mouseX: Int,
        mouseY: Int,
        predicate: (InputTarget) -> Boolean
    ): InputTarget? {
        for (index in targets.lastIndex downTo 0) {
            val target = targets[index]
            if (predicate(target) && target.contains(mouseX, mouseY)) {
                return target
            }
        }
        return null
    }
}

