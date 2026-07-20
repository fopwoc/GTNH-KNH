package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.util.AxisAlignedBB
import net.minecraftforge.client.event.RenderWorldLastEvent
import org.lwjgl.opengl.GL11
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@SideOnly(Side.CLIENT)
object MeasurementOverlayRenderer {
    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!MeasurementSession.isActive) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return
        val player = minecraft.thePlayer ?: return
        MeasurementWorldInteractionController.syncInteraction(minecraft)
        val currentDimensionId = world.provider.dimensionId
        val hoveredTarget = MeasurementInteractionState.currentHoveredTarget
        val hoveredBlock = hoveredTarget?.block

        val persistedMeasurements = MeasurementSelectionState.measurementsForDimension(currentDimensionId)
        val draftFirst = MeasurementSelectionState.draftFirst
        val draftSecond = MeasurementSelectionState.draftSecond
        val previewMeasurements = MeasurementSelectionState.previewMeasurementsForDimension(currentDimensionId)
        val hoveredTargetVisible = hoveredTarget != null &&
            MeasurementShortcutScheme.targetModifierDown() &&
            draftFirst == null &&
            !MeasurementSelectionState.isPastePlacementActive
        if (persistedMeasurements.isEmpty() && draftFirst == null && previewMeasurements.isEmpty() && !hoveredTargetVisible) {
            return
        }

        val partial = event.partialTicks.toDouble()
        val cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial
        val cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial
        val cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial
        val eyeX = cameraX
        val eyeY = cameraY + player.getEyeHeight().toDouble()
        val eyeZ = cameraZ

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_LINE_BIT or GL11.GL_COLOR_BUFFER_BIT)
        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(false)

        persistedMeasurements.filterNot { MeasurementSelectionState.isSelected(it.id) }.forEach { measurement ->
            drawMeasurement(
                mode = measurement.mode,
                first = measurement.first,
                second = measurement.second,
                style = MeasurementOverlayPalette.style(measurement.mode, OverlayVisualState.NORMAL),
                cameraX = cameraX,
                cameraY = cameraY,
                cameraZ = cameraZ
            )
        }
        persistedMeasurements.filter { MeasurementSelectionState.isSelected(it.id) }.forEach { measurement ->
            drawMeasurement(
                mode = measurement.mode,
                first = measurement.first,
                second = measurement.second,
                style = MeasurementOverlayPalette.style(measurement.mode, OverlayVisualState.SELECTED),
                cameraX = cameraX,
                cameraY = cameraY,
                cameraZ = cameraZ
            )
        }

        if (hoveredTargetVisible) {
            val hoverColor = MeasurementOverlayPalette.hoverColor(
                mode = MeasurementSession.mode,
                isOffsetTarget = hoveredTarget.kind == MeasurementHoverTargetKind.OFFSET
            )
            drawBlockOutline(hoveredTarget.block, cameraX, cameraY, cameraZ, hoverColor, 2.4f)
        }

        if (draftFirst != null) {
            val draftStyle = MeasurementOverlayPalette.style(MeasurementSession.mode, OverlayVisualState.NORMAL)
            drawBlockOutline(draftFirst, cameraX, cameraY, cameraZ, draftStyle.firstAnchorColor, 2.2f)
            draftSecond?.let { second ->
                val secondColor = when {
                    hoveredTarget?.block == second -> MeasurementOverlayPalette.hoverColor(
                        mode = MeasurementSession.mode,
                        isOffsetTarget = hoveredTarget.kind == MeasurementHoverTargetKind.OFFSET
                    )
                    else -> MeasurementOverlayPalette.draftSecondColor(
                        mode = MeasurementSession.mode,
                        isOffsetTarget = false
                    )
                }
                drawBlockOutline(second, cameraX, cameraY, cameraZ, secondColor, 2.2f)
                drawMeasurementShape(MeasurementSession.mode, draftFirst, second, draftStyle, cameraX, cameraY, cameraZ)
            }
        }

        val previewVisualState = when (MeasurementSelectionState.activeClipboard?.operation) {
            ClipboardOperation.MOVE -> OverlayVisualState.MOVE
            ClipboardOperation.RESIZE -> OverlayVisualState.RESIZE
            ClipboardOperation.COPY,
            ClipboardOperation.CUT,
            null -> OverlayVisualState.PASTE
        }
        previewMeasurements.forEach { measurement ->
            drawMeasurement(
                mode = measurement.mode,
                first = measurement.first,
                second = measurement.second,
                style = MeasurementOverlayPalette.style(measurement.mode, previewVisualState),
                cameraX = cameraX,
                cameraY = cameraY,
                cameraZ = cameraZ
            )
        }

        GL11.glDepthMask(true)
        GL11.glPopMatrix()
        GL11.glPopAttrib()

        persistedMeasurements.forEach { measurement ->
            val labelColor = if (MeasurementSelectionState.isSelected(measurement.id)) {
                MeasurementOverlayPalette.style(measurement.mode, OverlayVisualState.SELECTED).shapeColor(measurement.mode)
            } else {
                MeasurementOverlayPalette.style(measurement.mode, OverlayVisualState.NORMAL).shapeColor(measurement.mode)
            }
            drawMeasurementLabel(
                minecraft = minecraft,
                mode = measurement.mode,
                first = measurement.first,
                second = measurement.second,
                color = labelColor,
                cameraX = cameraX,
                cameraY = cameraY,
                cameraZ = cameraZ,
                eyeX = eyeX,
                eyeY = eyeY,
                eyeZ = eyeZ
            )
        }
        if (draftFirst != null && draftSecond != null) {
            drawMeasurementLabel(
                minecraft = minecraft,
                mode = MeasurementSession.mode,
                first = draftFirst,
                second = draftSecond,
                color = MeasurementOverlayPalette.style(MeasurementSession.mode, OverlayVisualState.NORMAL)
                    .shapeColor(MeasurementSession.mode),
                cameraX = cameraX,
                cameraY = cameraY,
                cameraZ = cameraZ,
                eyeX = eyeX,
                eyeY = eyeY,
                eyeZ = eyeZ
            )
        }
        previewMeasurements.forEach { measurement ->
            drawMeasurementLabel(
                minecraft = minecraft,
                mode = measurement.mode,
                first = measurement.first,
                second = measurement.second,
                color = MeasurementOverlayPalette.style(measurement.mode, previewVisualState)
                    .shapeColor(measurement.mode),
                cameraX = cameraX,
                cameraY = cameraY,
                cameraZ = cameraZ,
                eyeX = eyeX,
                eyeY = eyeY,
                eyeZ = eyeZ
            )
        }
    }

    private fun drawMeasurement(
        mode: MeasurementMode,
        first: BlockSelection,
        second: BlockSelection,
        style: MeasurementRenderStyle,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double
    ) {
        drawBlockOutline(first, cameraX, cameraY, cameraZ, style.firstAnchorColor, style.anchorWidth)
        drawBlockOutline(second, cameraX, cameraY, cameraZ, style.secondAnchorColor, style.anchorWidth)
        drawMeasurementShape(mode, first, second, style, cameraX, cameraY, cameraZ)
    }

    private fun drawMeasurementLabel(
        minecraft: Minecraft,
        mode: MeasurementMode,
        first: BlockSelection,
        second: BlockSelection,
        color: Color,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double
    ) {
        when (mode) {
            MeasurementMode.LINE -> drawDistanceLabel(minecraft, first, second, color, cameraX, cameraY, cameraZ, eyeX, eyeY, eyeZ)
            MeasurementMode.AREA -> drawAreaLabel(minecraft, first, second, color, cameraX, cameraY, cameraZ, eyeX, eyeY, eyeZ)
            MeasurementMode.SPHERE -> drawSphereLabel(minecraft, first, second, color, cameraX, cameraY, cameraZ, eyeX, eyeY, eyeZ)
            MeasurementMode.DISABLED -> Unit
        }
    }

    private fun drawMeasurementShape(
        mode: MeasurementMode,
        first: BlockSelection,
        second: BlockSelection,
        style: MeasurementRenderStyle,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double
    ) {
        when (mode) {
            MeasurementMode.LINE -> drawLine(first, second, style.lineColor, style.shapeWidth, cameraX, cameraY, cameraZ)
            MeasurementMode.AREA -> drawAreaOutline(first, second, style.areaColor, style.shapeWidth, cameraX, cameraY, cameraZ)
            MeasurementMode.SPHERE -> drawSphereOutline(first, second, style.areaColor, style.shapeWidth, cameraX, cameraY, cameraZ)
            MeasurementMode.DISABLED -> Unit
        }
    }


    private fun drawAreaOutline(
        first: BlockSelection,
        second: BlockSelection,
        color: Color,
        width: Float,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double
    ) {
        val minX = minOf(first.x, second.x).toDouble() - cameraX
        val minY = minOf(first.y, second.y).toDouble() - cameraY
        val minZ = minOf(first.z, second.z).toDouble() - cameraZ
        val maxX = maxOf(first.x, second.x).toDouble() + 1.0 - cameraX
        val maxY = maxOf(first.y, second.y).toDouble() + 1.0 - cameraY
        val maxZ = maxOf(first.z, second.z).toDouble() + 1.0 - cameraZ
        drawOutlinedBox(AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ), color, width)
    }

    private fun drawLine(
        first: BlockSelection,
        second: BlockSelection,
        color: Color,
        width: Float,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double
    ) {
        val rgb = rgb(color)
        GL11.glLineWidth(width)
        GL11.glColor4f(rgb.first, rgb.second, rgb.third, color.alpha / 255.0f)
        GL11.glBegin(GL11.GL_LINES)
        GL11.glVertex3d(first.centerX() - cameraX, first.centerY() - cameraY, first.centerZ() - cameraZ)
        GL11.glVertex3d(second.centerX() - cameraX, second.centerY() - cameraY, second.centerZ() - cameraZ)
        GL11.glEnd()
    }

    private fun drawSphereOutline(
        center: BlockSelection,
        edge: BlockSelection,
        color: Color,
        width: Float,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double
    ) {
        val radius = MeasurementGeometry.sphereRadius(center, edge)
        if (radius <= 1.0E-6) {
            return
        }

        val originX = center.centerX() - cameraX
        val originY = center.centerY() - cameraY
        val originZ = center.centerZ() - cameraZ
        drawCircle(originX, originY, originZ, radius, width, color, CirclePlane.XY)
        drawCircle(originX, originY, originZ, radius, width, color, CirclePlane.XZ)
        drawCircle(originX, originY, originZ, radius, width, color, CirclePlane.YZ)
    }

    private fun drawBlockOutline(
        selected: BlockSelection,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        color: Color,
        width: Float
    ) {
        val minX = selected.x.toDouble() - cameraX
        val minY = selected.y.toDouble() - cameraY
        val minZ = selected.z.toDouble() - cameraZ
        val box = AxisAlignedBB.getBoundingBox(minX, minY, minZ, minX + 1.0, minY + 1.0, minZ + 1.0)
        val rgb = rgb(color)

        GL11.glLineWidth(width)
        GL11.glColor4f(rgb.first, rgb.second, rgb.third, color.alpha / 255.0f)
        drawOutlinedBoxWithTessellator(Tessellator.instance, box)
    }

    private fun drawOutlinedBox(box: AxisAlignedBB, color: Color, width: Float) {
        val rgb = rgb(color)
        GL11.glLineWidth(width)
        GL11.glColor4f(rgb.first, rgb.second, rgb.third, color.alpha / 255.0f)
        drawOutlinedBoxWithTessellator(Tessellator.instance, box)
    }

    private fun drawOutlinedBoxWithTessellator(tessellator: Tessellator, box: AxisAlignedBB) {
        tessellator.startDrawing(GL11.GL_LINES)

        tessellator.addVertex(box.minX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.maxZ)
        tessellator.addVertex(box.maxX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.minZ)

        tessellator.addVertex(box.minX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.minZ)

        tessellator.addVertex(box.minX, box.minY, box.minZ)
        tessellator.addVertex(box.minX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.maxZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)
        tessellator.draw()
    }

    private fun drawDistanceLabel(
        minecraft: Minecraft,
        first: BlockSelection,
        second: BlockSelection,
        color: Color,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double
    ) {
        val label = "${MeasurementGeometry.formatDistance(MeasurementGeometry.lineDistance(first, second))} blocks"
        val anchor = MeasurementGeometry.closestPointOnSegment(first, second, eyeX, eyeY, eyeZ)
        drawWorldLabel(minecraft, label, anchor[0], anchor[1] + 0.35, anchor[2], cameraX, cameraY, cameraZ, color)
    }

    private fun drawAreaLabel(
        minecraft: Minecraft,
        first: BlockSelection,
        second: BlockSelection,
        color: Color,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double
    ) {
        val area = MeasurementGeometry.area(first, second)
        val anchor = MeasurementGeometry.preferredAreaLabelAnchor(first, second, eyeX, eyeY, eyeZ)
        drawWorldLabel(minecraft, area.label, anchor[0], anchor[1] + 0.35, anchor[2], cameraX, cameraY, cameraZ, color)
    }

    private fun drawSphereLabel(
        minecraft: Minecraft,
        first: BlockSelection,
        second: BlockSelection,
        color: Color,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double
    ) {
        val sphere = MeasurementGeometry.sphere(first, second)
        val anchor = MeasurementGeometry.preferredSphereLabelAnchor(first, second, eyeX, eyeY, eyeZ)
        drawWorldLabel(minecraft, sphere.label, anchor[0], anchor[1] + 0.15, anchor[2], cameraX, cameraY, cameraZ, color)
    }

    private fun drawWorldLabel(
        minecraft: Minecraft,
        label: String,
        worldX: Double,
        worldY: Double,
        worldZ: Double,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        color: Color
    ) {
        val renderManager = RenderManager.instance
        val fontRenderer = minecraft.fontRenderer
        val halfWidth = fontRenderer.getStringWidth(label) / 2

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_COLOR_BUFFER_BIT)
        GL11.glPushMatrix()
        GL11.glTranslated(worldX - cameraX, worldY - cameraY, worldZ - cameraZ)
        GL11.glNormal3f(0.0f, 1.0f, 0.0f)
        GL11.glRotatef(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f)
        GL11.glRotatef(renderManager.playerViewX, 1.0f, 0.0f, 0.0f)

        val scale = 0.026f
        GL11.glScalef(-scale, -scale, scale)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        fontRenderer.drawStringWithShadow(label, -halfWidth, 0, color.argbInt)
        GL11.glPopMatrix()
        GL11.glPopAttrib()
    }

    private fun rgb(color: Color): Triple<Float, Float, Float> = Triple(
        color.red / 255.0f,
        color.green / 255.0f,
        color.blue / 255.0f
    )

    private fun drawCircle(
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        radius: Double,
        width: Float,
        color: Color,
        plane: CirclePlane,
        segments: Int = 48
    ) {
        val rgb = rgb(color)
        GL11.glLineWidth(width)
        GL11.glColor4f(rgb.first, rgb.second, rgb.third, color.alpha / 255.0f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        repeat(segments) { index ->
            val angle = (index.toDouble() / segments.toDouble()) * PI * 2.0
            val cosAngle = cos(angle) * radius
            val sinAngle = sin(angle) * radius
            when (plane) {
                CirclePlane.XY -> GL11.glVertex3d(centerX + cosAngle, centerY + sinAngle, centerZ)
                CirclePlane.XZ -> GL11.glVertex3d(centerX + cosAngle, centerY, centerZ + sinAngle)
                CirclePlane.YZ -> GL11.glVertex3d(centerX, centerY + cosAngle, centerZ + sinAngle)
            }
        }
        GL11.glEnd()
    }

    private enum class CirclePlane {
        XY,
        XZ,
        YZ
    }

}

