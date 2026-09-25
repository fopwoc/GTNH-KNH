package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/** Raw game input from the loader hook, before vanilla pick-block handles middle click. */
object ModernMeasurementInput {
    fun onScroll(deltaY: Double): Boolean {
        if (!MeasurementSession.isActive || !ModernFreecamReach.isDetached ||
            !MeasurementShortcutScheme.editorModifierDown() || Minecraft.getInstance().gui.screen() != null || deltaY == 0.0) return false
        val step = if (MeasurementShortcutScheme.selectionModifierDown()) 8 else 1
        ModernFreecamReach.adjust(if (deltaY > 0) step else -step)
        return true
    }

    fun onMouseButton(button: Int, action: Int): Boolean =
        button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE &&
            action == GLFW.GLFW_PRESS &&
            Minecraft.getInstance().gui.screen() == null &&
            MeasurementWorldInteractionController.onMiddleClick()

    /** Escape reaches this as the pause menu opening; true keeps the menu closed, as on GTNH. */
    fun onPause(): Boolean = MeasurementSession.isActive && MeasurementSelectionState.cancelActiveInteraction()

    fun onKey(keyCode: Int, action: Int) {
        if (action != GLFW.GLFW_PRESS || !MeasurementSession.isActive || Minecraft.getInstance().gui.screen() != null) return
        // Handled by onPause, which knows whether Escape should still open the menu.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return
        val actions = MeasurementActionMapping.resolveKeyboardActions(
            MeasurementShortcutScheme.currentKeyboardSnapshot { key -> glfwCode(key) == keyCode }
        )
        actions.forEach { action ->
            when (action) {
                MeasurementKeyboardAction.CANCEL_ACTIVE_INTERACTION -> {
                    MeasurementSelectionState.cancelActiveInteraction()
                    return
                }
                MeasurementKeyboardAction.REDO -> {
                    MeasurementSelectionState.redo()
                    return
                }
                MeasurementKeyboardAction.UNDO -> {
                    MeasurementSelectionState.undo()
                    return
                }
                MeasurementKeyboardAction.COPY_SELECTION -> MeasurementSelectionState.copySelected()
                MeasurementKeyboardAction.CUT_SELECTION -> MeasurementSelectionState.cutSelected()
                MeasurementKeyboardAction.BEGIN_PASTE_PLACEMENT -> MeasurementSelectionState.beginPastePlacement()
                MeasurementKeyboardAction.DELETE_SELECTION_OR_CANCEL_DRAFT -> {
                    if (!MeasurementSelectionState.cancelDraftCreation()) MeasurementSelectionState.deleteSelected()
                }
            }
        }
    }

    private fun glfwCode(key: Key): Int = when (key) {
        Key.Escape -> GLFW.GLFW_KEY_ESCAPE
        Key.Z -> GLFW.GLFW_KEY_Z
        Key.Y -> GLFW.GLFW_KEY_Y
        Key.C -> GLFW.GLFW_KEY_C
        Key.X -> GLFW.GLFW_KEY_X
        Key.V -> GLFW.GLFW_KEY_V
        Key.Delete -> GLFW.GLFW_KEY_DELETE
        Key.Backspace -> GLFW.GLFW_KEY_BACKSPACE
        else -> GLFW.GLFW_KEY_UNKNOWN
    }
}
