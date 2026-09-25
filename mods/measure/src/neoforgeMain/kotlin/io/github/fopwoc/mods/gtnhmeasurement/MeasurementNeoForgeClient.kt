package io.github.fopwoc.mods.gtnhmeasurement

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementInput
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementOverlay
import net.minecraft.client.gui.screens.PauseScreen
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

internal object MeasurementNeoForgeClient {
    fun install() {
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent::class.java) { event ->
            ModernMeasurementOverlay.extract(event.camera.position())
        }
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseButton.Pre::class.java) { event ->
            if (ModernMeasurementInput.onMouseButton(event.button, event.action))
                event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener(InputEvent.Key::class.java) { event ->
            ModernMeasurementInput.onKey(event.key, event.action)
        }
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent::class.java) { event ->
            if (ModernMeasurementInput.onScroll(event.scrollDeltaY)) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening::class.java) { event ->
            if (event.newScreen is PauseScreen && ModernMeasurementInput.onPause())
                event.isCanceled = true
        }
    }
}
