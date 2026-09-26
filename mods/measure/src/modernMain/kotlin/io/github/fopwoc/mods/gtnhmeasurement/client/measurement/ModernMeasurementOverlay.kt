package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.minecraft.isHudHidden
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

/** The measurement shapes in the world; a framework world overlay. */
object ModernMeasurementOverlay {
    fun draw(eye: Vec3) {
        val dimensionId = ClientBackend.current.currentDimensionId ?: return
        MeasurementWorldInteractionController.syncInteraction()
        MeasurementOverlayPainter.paint(
            canvas = ModernMeasurementWorldCanvas(eye),
            currentDimensionId = dimensionId,
            active = MeasurementSession.isActive,
            hoveredTarget =
                if (MeasurementSession.isActive) MeasurementInteractionState.currentHoveredTarget
                else null,
            hideGui = Minecraft.getInstance().isHudHidden,
            targetModifierDown = MeasurementShortcutScheme.targetModifierDown(),
        )
    }
}
