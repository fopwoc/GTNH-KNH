package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.render.WorldOverlay
import io.github.fopwoc.mods.framework.render.WorldOverlayScope
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
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

    // F1: the shapes stay, the tooling (anchors, labels, hover) goes.
    val hideGui = minecraft.gameSettings.hideGUI

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
            drawMeasurement(
                measurement.mode,
                measurement.first,
                measurement.second,
                style,
                shapesOnly = hideGui,
                hoveredBlock = hoveredTarget?.block,
            )
            if (!hideGui) {
              drawMeasurementLabel(
                  measurement.mode,
                  measurement.first,
                  measurement.second,
                  style.shapeColor(measurement.mode),
              )
            }
          }
      if (hideGui) {
        previewMeasurements.forEach { measurement ->
          val style = MeasurementOverlayPalette.style(measurement.mode, previewVisualState)
          drawMeasurementShape(measurement.mode, measurement.first, measurement.second, style)
        }
        return@render
      }

      if (hoveredTargetVisible) {
        val offset = hoveredTarget.kind == MeasurementHoverTargetKind.OFFSET
        val color = MeasurementOverlayPalette.hoverColor(mode = mode, isOffsetTarget = offset)
        if (hoveredTarget.kind == MeasurementHoverTargetKind.ANCHOR) {
          anchor(hoveredTarget.block, color, 3.2f, hovered = true)
        } else {
          outline(hoveredTarget.block, color, 2.4f)
        }
      }

      if (draftFirst != null) {
        val draftStyle = MeasurementOverlayPalette.style(mode, OverlayVisualState.NORMAL)
        anchor(draftFirst, draftStyle.firstAnchorColor, 2.2f, hovered = false)
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
          anchor(second, secondColor, 2.2f, hovered = false)
          drawMeasurementShape(mode, draftFirst, second, draftStyle)
          drawMeasurementLabel(mode, draftFirst, second, draftStyle.shapeColor(mode))
        }
      }

      previewMeasurements.forEach { measurement ->
        val style = MeasurementOverlayPalette.style(measurement.mode, previewVisualState)
        drawMeasurement(
            measurement.mode,
            measurement.first,
            measurement.second,
            style,
            shapesOnly = false,
            hoveredBlock = null,
        )
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

  /** Corner brackets around a faint glass core; a hovered anchor breathes. */
  private fun WorldOverlayScope.anchor(
      block: BlockSelection,
      color: Color,
      width: Float,
      hovered: Boolean,
  ) {
    val x = block.x.toDouble()
    val y = block.y.toDouble()
    val z = block.z.toDouble()
    val grow =
        if (hovered) {
          val phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / PULSE_PERIOD_MS.toDouble()
          PULSE_GROW * (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI))
        } else 0.0
    filledBox(x, y, z, x + 1, y + 1, z + 1, color.copy(alpha = if (hovered) 70 else 40))
    cornerBrackets(x, y, z, x + 1, y + 1, z + 1, color, width, arm = 0.3, grow = grow)
  }

  private fun WorldOverlayScope.drawMeasurement(
      mode: MeasurementMode,
      first: BlockSelection,
      second: BlockSelection,
      style: MeasurementRenderStyle,
      shapesOnly: Boolean,
      hoveredBlock: BlockSelection?,
  ) {
    if (!shapesOnly) {
      anchor(first, style.firstAnchorColor, style.anchorWidth, hovered = first == hoveredBlock)
      anchor(second, style.secondAnchorColor, style.anchorWidth, hovered = second == hoveredBlock)
    }
    drawMeasurementShape(mode, first, second, style)
  }

  private const val PULSE_PERIOD_MS = 1200L
  private const val PULSE_GROW = 0.12

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
      MeasurementMode.AREA -> {
        val minX = minOf(first.x, second.x).toDouble()
        val minY = minOf(first.y, second.y).toDouble()
        val minZ = minOf(first.z, second.z).toDouble()
        val maxX = maxOf(first.x, second.x) + 1.0
        val maxY = maxOf(first.y, second.y) + 1.0
        val maxZ = maxOf(first.z, second.z) + 1.0
        glassBox(minX, minY, minZ, maxX, maxY, maxZ, style.areaColor)
      }
      MeasurementMode.SPHERE -> {
        val radius = MeasurementGeometry.sphereRadius(first, second)
        if (radius > 1.0E-6) {
          val cx = first.centerX()
          val cy = first.centerY()
          val cz = first.centerZ()
          glassSphere(cx, cy, cz, radius, style.areaColor, MeasurementConfig.sphereGrid)
          if (MeasurementConfig.sphereRadiusLines) {
            // The radius as it was clicked, and the faint axis diameters: their ends on the shell
            // are where the outermost blocks go on each axis.
            line(
                cx,
                cy,
                cz,
                second.centerX(),
                second.centerY(),
                second.centerZ(),
                style.areaColor,
                style.shapeWidth,
            )
            val faint = style.areaColor.copy(alpha = style.areaColor.alpha / 3)
            line(cx - radius, cy, cz, cx + radius, cy, cz, faint, 1f)
            line(cx, cy - radius, cz, cx, cy + radius, cz, faint, 1f)
            line(cx, cy, cz - radius, cx, cy, cz + radius, faint, 1f)
          }
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
