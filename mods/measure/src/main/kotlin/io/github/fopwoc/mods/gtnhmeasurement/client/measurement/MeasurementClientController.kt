package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.InputEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.serialization.WorldScopedSync
import io.github.fopwoc.mods.gtnhmeasurement.client.compat.FreecamCompat
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.event.world.WorldEvent
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
object MeasurementClientController {
  // Undo/redo and drags mark the store dirty many times per second; batch the JSON writes.
  private val persistence =
      WorldScopedSync(
          store = MeasurementPersistence.measurements,
          debounceTicks = 20,
          onLoaded = { loaded ->
            if (loaded == null) MeasurementSelectionState.resetAll()
            else MeasurementSelectionState.replacePersistedMeasurements(loaded.measurements)
          },
          snapshot = {
            PersistedMeasurementSet(
                measurements = MeasurementSelectionState.exportPersistedMeasurements()
            )
          },
      )

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END) {
      return
    }

    FreecamCompat.tick()
    val minecraft = Minecraft.getMinecraft()
    if (MeasurementSelectionState.consumePersistenceDirtyFlag()) {
      persistence.markDirty()
    }
    persistence.tick()
    minecraft.theWorld?.provider?.dimensionId?.let(MeasurementSelectionState::syncForDimension)
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
      if (MeasurementSelectionState.consumePersistenceDirtyFlag()) {
        persistence.markDirty()
      }
      persistence.flush()
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
