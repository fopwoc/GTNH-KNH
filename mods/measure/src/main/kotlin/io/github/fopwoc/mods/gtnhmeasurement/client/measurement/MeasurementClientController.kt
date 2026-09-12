package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.InputEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.serialization.WorldScopedSync
import io.github.fopwoc.mods.gtnhmeasurement.client.compat.FreecamCompat
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.event.world.WorldEvent
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
object MeasurementClientController {
  private const val CONFIG_POLL_TICKS = 100
  private var ticks = 0
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
    if (event.phase == TickEvent.Phase.START) {
      suppressSneakWhileConstraining()
      return
    }

    FreecamCompat.tick()
    if (++ticks % CONFIG_POLL_TICKS == 0) {
      MeasurementConfig.refreshIfChanged()
    }
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

  /**
   * While the second anchor or a placement is live, Shift is the constraint modifier and must not
   * also sneak (which is "descend" for every kind of flight). Shift + Space descends instead: the
   * raw keyboard is read, then the game's sneak/jump key states are rewritten before the player
   * ticks. The modifier itself is read from the raw keyboard, so constraining is unaffected.
   */
  private fun suppressSneakWhileConstraining() {
    if (!MeasurementSession.isActive) {
      return
    }
    if (
        !MeasurementSelectionState.hasActiveDraftCreation &&
            !MeasurementSelectionState.isPastePlacementActive
    ) {
      return
    }
    val minecraft = Minecraft.getMinecraft()
    if (minecraft.currentScreen != null) {
      return
    }
    val sneakKey = minecraft.gameSettings.keyBindSneak.keyCode
    val jumpKey = minecraft.gameSettings.keyBindJump.keyCode
    val shiftDown = Keyboard.isKeyDown(sneakKey)
    val spaceDown = Keyboard.isKeyDown(jumpKey)
    when {
      shiftDown && spaceDown -> {
        KeyBinding.setKeyBindState(jumpKey, false)
        KeyBinding.setKeyBindState(sneakKey, true)
      }
      else -> {
        KeyBinding.setKeyBindState(sneakKey, false)
        KeyBinding.setKeyBindState(jumpKey, spaceDown)
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
