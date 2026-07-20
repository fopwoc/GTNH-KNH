package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.MouseEvent

@SideOnly(Side.CLIENT)
object MeasurementWorldInteractionController {
    fun syncInteraction(minecraft: Minecraft) {
        if (!MeasurementSession.isActive) {
            MeasurementInteractionState.clearHoveredTarget()
            MeasurementSelectionState.updateDraftPreview(block = null, mode = MeasurementSession.mode)
            MeasurementSelectionState.updatePastePreview(null)
            return
        }

        val world = minecraft.theWorld
        if (world == null) {
            MeasurementInteractionState.clearHoveredTarget()
            MeasurementSelectionState.updateDraftPreview(block = null, mode = MeasurementSession.mode)
            MeasurementSelectionState.updatePastePreview(null)
            return
        }

        val currentDimensionId = world.provider.dimensionId
        MeasurementSelectionState.syncForDimension(currentDimensionId)
        val hoveredTarget = MeasurementHoverResolver.resolve(
            minecraft = minecraft,
            currentDimensionId = currentDimensionId,
            usePlacementOffset = MeasurementShortcutScheme.targetModifierDown()
        )
        val inputSnapshot = MeasurementShortcutScheme.currentWorldClickSnapshot()
        MeasurementInteractionState.updateHoveredTarget(hoveredTarget)

        val hoveredBlock = hoveredTarget?.block
        MeasurementSelectionState.updateDraftPreview(
            block = if (MeasurementSelectionState.draftFirst != null && !MeasurementSelectionState.isPastePlacementActive) hoveredBlock else null,
            mode = MeasurementSession.mode,
            constrainToRightAngles = inputSnapshot.selectionModifierDown
        )
        MeasurementSelectionState.updatePastePreview(
            block = if (MeasurementSelectionState.isPastePlacementActive) hoveredBlock else null,
            constrainToRightAngles = inputSnapshot.selectionModifierDown
        )
    }

    @SubscribeEvent
    fun onMouse(event: MouseEvent) {
        if (event.button != 2 || !event.buttonstate || !MeasurementSession.isActive) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return
        val clicked = MeasurementHoverResolver.resolve(
            minecraft = minecraft,
            currentDimensionId = world.provider.dimensionId,
            usePlacementOffset = MeasurementShortcutScheme.targetModifierDown()
        )?.block ?: return
        val inputSnapshot = MeasurementShortcutScheme.currentWorldClickSnapshot()
        val action = MeasurementActionMapping.resolveWorldClickAction(
            snapshot = inputSnapshot,
            isPastePlacementActive = MeasurementSelectionState.isPastePlacementActive,
            hasActiveDraftCreation = MeasurementSelectionState.hasActiveDraftCreation
        )
        val handled = when (action) {
            MeasurementWorldClickAction.PLACE_CLIPBOARD -> MeasurementSelectionState.placeClipboardAt(
                anchor = clicked,
                constrainToRightAngles = inputSnapshot.selectionModifierDown
            )
            MeasurementWorldClickAction.SELECT_MULTI -> MeasurementSelectionState.selectAtAnchor(clicked, multiSelect = true)
            MeasurementWorldClickAction.SELECT_SINGLE -> MeasurementSelectionState.selectAtAnchor(clicked, multiSelect = false)
            MeasurementWorldClickAction.BEGIN_TRANSFORM -> MeasurementSelectionState.beginMoveAtAnchor(clicked)
            MeasurementWorldClickAction.REGISTER_ANCHOR -> MeasurementSelectionState.registerMeasurementAnchor(
                clicked = clicked,
                mode = MeasurementSession.mode,
                constrainToRightAngles = inputSnapshot.selectionModifierDown
            )
        }

        if (!handled) {
            return
        }

        syncInteraction(minecraft)
        event.isCanceled = true
    }
}

