package io.github.fopwoc.mods.gtnhmeasurement.client

import cpw.mods.fml.client.registry.ClientRegistry
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.InputEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.MeasurementScreenController
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard

/** Unbound by default; assign it under Options → Controls → Measure. */
@SideOnly(Side.CLIENT)
object MeasurementKeyBindings {
  val openMenu = KeyBinding("key.measure.openMenu", Keyboard.KEY_NONE, "key.categories.measure")

  fun register() {
    ClientRegistry.registerKeyBinding(openMenu)
  }

  @SubscribeEvent
  fun onKeyInput(event: InputEvent.KeyInputEvent) {
    if (openMenu.isPressed) {
      MeasurementScreenController.requestOpen()
    }
  }
}
