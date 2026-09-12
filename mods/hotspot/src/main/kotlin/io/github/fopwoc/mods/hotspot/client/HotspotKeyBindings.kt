package io.github.fopwoc.mods.hotspot.client

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientKeyBindings
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.hotspot.client.gui.HotspotScreen
import net.minecraft.client.settings.KeyBinding

/** Unbound by default; assign it under Options → Controls → Hotspot. */
@SideOnly(Side.CLIENT)
object HotspotKeyBindings {
  lateinit var openMenu: KeyBinding
    private set

  fun register() {
    openMenu =
        ClientKeyBindings.bind("key.hotspot.openMenu", "key.categories.hotspot") {
          ScreenOpener.open(::HotspotScreen)
        }
  }
}
