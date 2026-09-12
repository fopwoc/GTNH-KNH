package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.render.WorldOverlay
import io.github.fopwoc.mods.framework.render.WorldOverlayScope
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderWorldLastEvent

@SideOnly(Side.CLIENT)
object MeasurementOverlayRenderer {
  @SubscribeEvent
  fun onRenderWorld(event: RenderWorldLastEvent) {
    val minecraft = Minecraft.getMinecraft()
    // Runs per frame so hover previews follow the crosshair smoothly; it also clears interaction
    // state while measuring is disabled.
    MeasurementWorldInteractionController.syncInteraction(minecraft)
    val active = MeasurementSession.isActive

    val world = minecraft.theWorld ?: return
    val currentDimensionId = world.provider.dimensionId
    val hoveredTarget = if (active) MeasurementInteractionState.currentHoveredTarget else null

    // With measuring off, only what was picked in the menu is drawn so it can be located in the
    // world; everything else stays out of the way.
    val persistedMeasurements =
        if (active) MeasurementSelectionState.measurementsForDimension(currentDimensionId)
        else MeasurementSelectionState.selectedMeasurementsForDimension(currentDimensionId)
    val draftFirst = if (active) MeasurementSelectionState.draftFirst else null
    val draftSecond = if (active) MeasurementSelectionState.draftSecond else null
    val previewMeasurements =
        if (active) MeasurementSelectionState.previewMeasurementsForDimension(currentDimensionId)
        else emptyList()
    // Outline only what is worth pointing out: an anchor ready to be grabbed, or the face-offset
    // target while Ctrl is held. Drafts and placements draw their own previews.
    val hoveredTargetVisible =
        hoveredTarget != null &&
            draftFirst == null &&
            !MeasurementSelectionState.isPastePlacementActive &&
            (hoveredTarget.kind == MeasurementHoverTargetKind.ANCHOR ||
                MeasurementShortcutScheme.targetModifierDown())
    if (
        persistedMeasurements.isEmpty() &&
            draftFirst == null &&
            previewMeasurements.isEmpty() &&
            !hoveredTargetVisible
    ) {
      return
    }

    val hoveredMeasurementIds =
        if (hoveredTarget?.kind == MeasurementHoverTargetKind.ANCHOR) {
          MeasurementSelectionState.measurementsContainingBlock(hoveredTarget.block)
              .mapTo(HashSet(), MeasurementRecord::id)
        } else {
          emptySet()
        }
    val previewVisualState =
        when (MeasurementSelectionState.activeClipboard?.operation) {
          ClipboardOperation.MOVE -> OverlayVisualState.MOVE
          ClipboardOperation.RESIZE -> OverlayVisualState.RESIZE
          ClipboardOperation.COPY,
          ClipboardOperation.CUT,
          null -> OverlayVisualState.PASTE
        }
    val mode = MeasurementSession.mode

    WorldOverlay.render(event.partialTicks) {
      // Selected ones last so they sit on top of their neighbours.
      persistedMeasurements
          .sortedBy { MeasurementSelectionState.isSelected(it.id) }
          .forEach { measurement ->
            val visualState =
                when {
                  MeasurementSelectionState.isSelected(measurement.id) ->
                      OverlayVisualState.SELECTED
                  measurement.id in hoveredMeasurementIds -> OverlayVisualState.HOVERED
                  else -> OverlayVisualState.NORMAL
                }
            val style = MeasurementOverlayPalette.style(measurement.mode, visualState)
            drawMeasurement(measurement.mode, measurement.first, measurement.second, style)
            drawMeasurementLabel(
                measurement.mode,
                measurement.first,
                measurement.second,
                style.shapeColor(measurement.mode),
            )
          }

      if (hoveredTargetVisible) {
        val offset = hoveredTarget.kind == MeasurementHoverTargetKind.OFFSET
        val color = MeasurementOverlayPalette.hoverColor(mode = mode, isOffsetTarget = offset)
        val width = if (hoveredTarget.kind == MeasurementHoverTargetKind.ANCHOR) 3.2f else 2.4f
        outline(hoveredTarget.block, color, width)
      }

      if (draftFirst != null) {
        val draftStyle = MeasurementOverlayPalette.style(mode, OverlayVisualState.NORMAL)
        outline(draftFirst, draftStyle.firstAnchorColor, 2.2f)
        draftSecond?.let { second ->
          val secondColor =
              if (hoveredTarget?.block == second) {
                MeasurementOverlayPalette.hoverColor(
                    mode = mode,
                    isOffsetTarget = hoveredTarget.kind == MeasurementHoverTargetKind.OFFSET,
                )
              } else {
                MeasurementOverlayPalette.draftSecondColor(mode = mode, isOffsetTarget = false)
              }
          outline(second, secondColor, 2.2f)
          drawMeasurementShape(mode, draftFirst, second, draftStyle)
          drawMeasurementLabel(mode, draftFirst, second, draftStyle.shapeColor(mode))
        }
      }

      previewMeasurements.forEach { measurement ->
        val style = MeasurementOverlayPalette.style(measurement.mode, previewVisualState)
        drawMeasurement(measurement.mode, measurement.first, measurement.second, style)
        drawMeasurementLabel(
            measurement.mode,
            measurement.first,
            measurement.second,
            style.shapeColor(measurement.mode),
        )
      }
    }
  }

  private fun WorldOverlayScope.outline(block: BlockSelection, color: Color, width: Float) =
      blockOutline(block.x, block.y, block.z, color, width)

  private fun WorldOverlayScope.drawMeasurement(
      mode: MeasurementMode,
      first: BlockSelection,
      second: BlockSelection,
      style: MeasurementRenderStyle,
  ) {
    outline(first, style.firstAnchorColor, style.anchorWidth)
    outline(second, style.secondAnchorColor, style.anchorWidth)
    drawMeasurementShape(mode, first, second, style)
  }

  private fun WorldOverlayScope.drawMeasurementShape(
      mode: MeasurementMode,
      first: BlockSelection,
      second: BlockSelection,
      style: MeasurementRenderStyle,
  ) {
    when (mode) {
      MeasurementMode.LINE ->
          line(
              first.centerX(),
              first.centerY(),
              first.centerZ(),
              second.centerX(),
              second.centerY(),
              second.centerZ(),
              style.lineColor,
              style.shapeWidth,
          )
      MeasurementMode.AREA ->
          glassBox(
              minOf(first.x, second.x).toDouble(),
              minOf(first.y, second.y).toDouble(),
              minOf(first.z, second.z).toDouble(),
              maxOf(first.x, second.x) + 1.0,
              maxOf(first.y, second.y) + 1.0,
              maxOf(first.z, second.z) + 1.0,
              style.areaColor,
          )
      MeasurementMode.SPHERE -> {
        val radius = MeasurementGeometry.sphereRadius(first, second)
        if (radius > 1.0E-6) {
          glassSphere(first.centerX(), first.centerY(), first.centerZ(), radius, style.areaColor)
        }
      }
      MeasurementMode.DISABLED -> Unit
    }
  }

  private fun WorldOverlayScope.drawMeasurementLabel(
      mode: MeasurementMode,
      first: BlockSelection,
      second: BlockSelection,
      color: Color,
  ) {
    val eyeX = camera.eyeX
    val eyeY = camera.eyeY
    val eyeZ = camera.eyeZ
    when (mode) {
      MeasurementMode.LINE -> {
        val text =
            "${MeasurementGeometry.formatDistance(MeasurementGeometry.lineDistance(first, second))} blocks"
        val anchor = MeasurementGeometry.closestPointOnSegment(first, second, eyeX, eyeY, eyeZ)
        label(anchor[0], anchor[1] + 0.35, anchor[2], text, color)
      }
      MeasurementMode.AREA -> {
        val anchor = MeasurementGeometry.preferredAreaLabelAnchor(first, second, eyeX, eyeY, eyeZ)
        label(
            anchor[0],
            anchor[1] + 0.35,
            anchor[2],
            MeasurementGeometry.area(first, second).label,
            color,
        )
      }
      MeasurementMode.SPHERE -> {
        val anchor = MeasurementGeometry.preferredSphereLabelAnchor(first, second, eyeX, eyeY, eyeZ)
        label(
            anchor[0],
            anchor[1] + 0.15,
            anchor[2],
            MeasurementGeometry.sphere(first, second).label,
            color,
        )
      }
      MeasurementMode.DISABLED -> Unit
    }
  }
}
