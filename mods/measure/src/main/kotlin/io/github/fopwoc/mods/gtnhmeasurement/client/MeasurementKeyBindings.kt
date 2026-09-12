package io.github.fopwoc.mods.gtnhmeasurement.client

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientKeyBindings
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.MeasurementModeScreen
import net.minecraft.client.settings.KeyBinding

/** Unbound by default; assign it under Options → Controls → Measure. */
@SideOnly(Side.CLIENT)
object MeasurementKeyBindings {
  lateinit var openMenu: KeyBinding
    private set

  fun register() {
    openMenu =
        ClientKeyBindings.bind("key.measure.openMenu", "key.categories.measure") {
          ScreenOpener.open(::MeasurementModeScreen)
        }
  }
}
