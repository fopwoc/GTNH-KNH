package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.SimpleGizmoCollector
import net.minecraft.world.phys.Vec3

/** Called in level extraction by the loader-specific event. */
object ModernMeasurementOverlay {
    fun extract(cameraPosition: Vec3) {
        val minecraft = Minecraft.getInstance()
        val dimensionId = ClientBackend.current.currentDimensionId ?: return
        MeasurementWorldInteractionController.syncInteraction()
        val collector = SimpleGizmoCollector()
        Gizmos.withCollector(collector).use {
            MeasurementOverlayPainter.paint(
                canvas = ModernMeasurementWorldCanvas(cameraPosition),
                currentDimensionId = dimensionId,
                active = MeasurementSession.isActive,
                hoveredTarget =
                    if (MeasurementSession.isActive)
                        MeasurementInteractionState.currentHoveredTarget
                    else null,
                hideGui = minecraft.gui.hud.isHidden,
                targetModifierDown = MeasurementShortcutScheme.targetModifierDown(),
            )
        }
        minecraft.levelRenderer.addMainThreadGizmos(collector.drainGizmos())
    }
}
