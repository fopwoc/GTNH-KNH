package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.render.WorldOverlay
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderWorldLastEvent

@SideOnly(Side.CLIENT)
object MeasurementOverlayRenderer {
    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        val minecraft = Minecraft.getMinecraft()
        MeasurementWorldInteractionController.syncInteraction(minecraft)
        val dimensionId = minecraft.theWorld?.provider?.dimensionId?.toString() ?: return
        val active = MeasurementSession.isActive
        val hovered = if (active) MeasurementInteractionState.currentHoveredTarget else null

        WorldOverlay.render(event.partialTicks) {
            MeasurementOverlayPainter.paint(
                canvas = GtnhMeasurementWorldCanvas(this),
                currentDimensionId = dimensionId,
                active = active,
                hoveredTarget = hovered,
                hideGui = minecraft.gameSettings.hideGUI,
                targetModifierDown = MeasurementShortcutScheme.targetModifierDown(),
            )
        }
    }
}
