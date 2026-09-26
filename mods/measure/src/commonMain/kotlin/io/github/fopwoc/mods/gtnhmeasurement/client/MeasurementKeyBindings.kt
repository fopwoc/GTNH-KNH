package io.github.fopwoc.mods.gtnhmeasurement.client

import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBindings
import io.github.fopwoc.mods.framework.ui.compose.screen.Screens
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.MeasurementModeScreen

/** Unbound by default; assign it under Options → Controls → Measure. */
object MeasurementKeyBindings {
    lateinit var openMenu: KeyBinding
        private set

    fun register() {
        openMenu =
            KeyBindings.register("key.measure.openMenu", "measure") {
                Screens.open(MeasurementModeScreen())
            }
    }
}
