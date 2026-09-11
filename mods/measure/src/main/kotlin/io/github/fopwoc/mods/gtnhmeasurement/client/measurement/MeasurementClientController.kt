package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.InputEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.event.world.WorldEvent
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
object MeasurementClientController {
  // Undo/redo and drags mark the store dirty many times per second; batch the JSON writes.
  private const val SAVE_DEBOUNCE_TICKS = 20

  private var loadedContextId: String? = null
  private var dirtySinceTick: Int? = null
  private var tickCounter = 0

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END) {
      return
    }

    tickCounter++
    val minecraft = Minecraft.getMinecraft()
    syncPersistenceContext(minecraft)
    minecraft.theWorld?.provider?.dimensionId?.let(MeasurementSelectionState::syncForDimension)
    flushDirtyMeasurements(force = false)
  }

  /**
   * Minecraft only fires this while no screen is open, once per LWJGL keyboard event, so presses
   * are never missed and never double-counted.
   */
  @SubscribeEvent
  fun onKeyInput(event: InputEvent.KeyInputEvent) {
    if (!MeasurementSession.isActive || !Keyboard.getEventKeyState()) {
      return
    }

    val pressedKey = Keyboard.getEventKey()
    handleShortcuts { keyCode -> keyCode == pressedKey }
  }

  private fun syncPersistenceContext(minecraft: Minecraft) {
    val resolvedContextId = MeasurementPersistence.resolveContextId(minecraft)
    if (loadedContextId == resolvedContextId) {
      return
    }

    flushDirtyMeasurements(force = true)
    loadedContextId = resolvedContextId
    if (resolvedContextId == null) {
      MeasurementSelectionState.resetAll()
      return
    }

    MeasurementSelectionState.replacePersistedMeasurements(
        MeasurementPersistence.loadMeasurements(resolvedContextId)
    )
  }

  private fun flushDirtyMeasurements(force: Boolean) {
    val activeContextId = loadedContextId ?: return
    if (MeasurementSelectionState.consumePersistenceDirtyFlag() && dirtySinceTick == null) {
      dirtySinceTick = tickCounter
    }
    val since = dirtySinceTick ?: return
    if (!force && tickCounter - since < SAVE_DEBOUNCE_TICKS) {
      return
    }

    dirtySinceTick = null
    MeasurementPersistence.saveMeasurements(
        contextId = activeContextId,
        measurements = MeasurementSelectionState.exportPersistedMeasurements(),
    )
  }

  private fun handleShortcuts(keyPressed: (Int) -> Boolean) {
    val actions =
        MeasurementActionMapping.resolveKeyboardActions(
            MeasurementShortcutScheme.currentKeyboardSnapshot(keyPressed)
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
        MeasurementKeyboardAction.COPY_SELECTION -> {
          MeasurementSelectionState.copySelected()
        }
        MeasurementKeyboardAction.CUT_SELECTION -> {
          MeasurementSelectionState.cutSelected()
        }
        MeasurementKeyboardAction.BEGIN_PASTE_PLACEMENT -> {
          MeasurementSelectionState.beginPastePlacement()
        }
        MeasurementKeyboardAction.DELETE_SELECTION_OR_CANCEL_DRAFT -> {
          if (!MeasurementSelectionState.cancelDraftCreation()) {
            MeasurementSelectionState.deleteSelected()
          }
        }
      }
    }
  }

  /** Fires on quit-to-menu and on shutdown, before the client world is dropped. */
  @SubscribeEvent
  fun onWorldUnload(event: WorldEvent.Unload) {
    if (event.world.isRemote) {
      flushDirtyMeasurements(force = true)
    }
  }

  @SubscribeEvent
  fun onGuiOpen(event: GuiOpenEvent) {
    if (!MeasurementSession.isActive) {
      return
    }

    if (event.gui is GuiIngameMenu && MeasurementSelectionState.cancelActiveInteraction()) {
      event.isCanceled = true
    }
  }
}
