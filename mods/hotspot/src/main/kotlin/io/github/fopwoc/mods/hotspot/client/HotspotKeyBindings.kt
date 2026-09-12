package io.github.fopwoc.mods.hotspot.client

import cpw.mods.fml.client.registry.ClientRegistry
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.InputEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.hotspot.client.gui.HotspotScreenController
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard

/** Unbound by default; assign it under Options → Controls → Hotspot. */
@SideOnly(Side.CLIENT)
object HotspotKeyBindings {
  val openMenu = KeyBinding("key.hotspot.openMenu", Keyboard.KEY_NONE, "key.categories.hotspot")

  fun register() {
    ClientRegistry.registerKeyBinding(openMenu)
  }

  @SubscribeEvent
  fun onKeyInput(event: InputEvent.KeyInputEvent) {
    if (openMenu.isPressed) {
      HotspotScreenController.requestOpen()
    }
  }
}
