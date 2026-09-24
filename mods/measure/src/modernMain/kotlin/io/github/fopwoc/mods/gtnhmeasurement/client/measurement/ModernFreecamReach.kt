package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import net.minecraft.client.Minecraft

/** Reach for detached modern cameras; resets when the viewer changes. */
object ModernFreecamReach {
    var reach = MeasurementConfig.freecamReach
        private set

    private var wasDetached = false

    val isDetached: Boolean
        get() = Minecraft.getInstance().let { it.player != null && it.cameraEntity !== it.player }

    fun install() {
        ClientEvents.tickEnd.subscribe {
            val detached = isDetached
            if (detached != wasDetached) {
                wasDetached = detached
                reach = MeasurementConfig.freecamReach
            }
        }
    }

    fun adjust(steps: Int) {
        reach = (reach + steps).coerceIn(1, 128)
    }
}
