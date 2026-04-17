package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraftforge.client.event.GuiOpenEvent
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
object MeasurementClientController {
    private var loadedContextId: String? = null
    private val previousKeyStates = HashMap<Int, Boolean>()

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        syncPersistenceContext(minecraft)
        minecraft.theWorld?.provider?.dimensionId?.let(MeasurementSelectionState::syncForDimension)

        if (minecraft.currentScreen == null && MeasurementSession.isActive) {
            handleShortcuts()
        } else {
            resetTrackedKeys()
        }

        flushDirtyMeasurements()
    }

    private fun syncPersistenceContext(minecraft: Minecraft) {
        val resolvedContextId = MeasurementPersistence.resolveContextId(minecraft)
        if (loadedContextId == resolvedContextId) {
            return
        }

        flushDirtyMeasurements()
        loadedContextId = resolvedContextId
        if (resolvedContextId == null) {
            MeasurementSelectionState.resetAll()
            return
        }

        MeasurementSelectionState.replacePersistedMeasurements(
            MeasurementPersistence.loadMeasurements(resolvedContextId)
        )
    }

    private fun flushDirtyMeasurements() {
        val activeContextId = loadedContextId ?: return
        if (!MeasurementSelectionState.consumePersistenceDirtyFlag()) {
            return
        }

        MeasurementPersistence.saveMeasurements(
            contextId = activeContextId,
            measurements = MeasurementSelectionState.exportPersistedMeasurements()
        )
    }

    private fun handleShortcuts() {
        if (MeasurementShortcutScheme.redoPrimaryPressed(::keyPressed) ||
            MeasurementShortcutScheme.redoSecondaryPressed(::keyPressed)
        ) {
            MeasurementSelectionState.redo()
            return
        }
        if (MeasurementShortcutScheme.editorModifierDown() && keyPressed(Keyboard.KEY_Z)) {
            MeasurementSelectionState.undo()
            return
        }
        if (MeasurementShortcutScheme.editorModifierDown() && keyPressed(Keyboard.KEY_C)) {
            MeasurementSelectionState.copySelected()
        }
        if (MeasurementShortcutScheme.editorModifierDown() && keyPressed(Keyboard.KEY_X)) {
            MeasurementSelectionState.cutSelected()
        }
        if (MeasurementShortcutScheme.editorModifierDown() && keyPressed(Keyboard.KEY_V)) {
            MeasurementSelectionState.beginPastePlacement()
        }
        if (keyPressed(Keyboard.KEY_DELETE) || keyPressed(Keyboard.KEY_BACK)) {
            if (!MeasurementSelectionState.cancelDraftCreation()) {
                MeasurementSelectionState.deleteSelected()
            }
        }
    }

    @SubscribeEvent
    fun onGuiOpen(event: GuiOpenEvent) {
        if (!MeasurementSession.isActive || !MeasurementSelectionState.hasActiveDraftCreation) {
            return
        }

        if (event.gui is GuiIngameMenu) {
            MeasurementSelectionState.cancelDraftCreation()
            event.isCanceled = true
        }
    }

    private fun resetTrackedKeys() {
        previousKeyStates.clear()
    }

    private fun keyPressed(keyCode: Int): Boolean {
        val currentlyDown = Keyboard.isKeyDown(keyCode)
        val wasDown = previousKeyStates.put(keyCode, currentlyDown) ?: false
        return currentlyDown && !wasDown
    }

}

