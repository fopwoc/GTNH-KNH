package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.render.GlassGrid
import io.github.fopwoc.mods.framework.render.WorldOverlayScope
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

internal class GtnhMeasurementWorldCanvas(private val scope: WorldOverlayScope) : MeasurementWorldCanvas {
    override val eyeX: Double get() = scope.camera.eyeX
    override val eyeY: Double get() = scope.camera.eyeY
    override val eyeZ: Double get() = scope.camera.eyeZ

    override fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, color: Color, width: Float) =
        scope.line(x1, y1, z1, x2, y2, z2, color, width)

    override fun blockOutline(x: Int, y: Int, z: Int, color: Color, width: Float) = scope.blockOutline(x, y, z, color, width)

    override fun cornerBrackets(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Color, width: Float, arm: Double, grow: Double,
    ) = scope.cornerBrackets(minX, minY, minZ, maxX, maxY, maxZ, color, width, arm, grow)

    override fun filledBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Color) =
        scope.filledBox(minX, minY, minZ, maxX, maxY, maxZ, color)

    override fun glassBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Color) {
        scope.glassBox(minX, minY, minZ, maxX, maxY, maxZ, color)
    }

    override fun glassSphere(centerX: Double, centerY: Double, centerZ: Double, radius: Double, color: Color, grid: GlassGrid) =
        scope.glassSphere(centerX, centerY, centerZ, radius, color, grid)

    override fun label(x: Double, y: Double, z: Double, text: String, color: Color) = scope.label(x, y, z, text, color)

    override fun ghosted(draw: (Int?) -> Unit) = scope.ghosted(draw = draw)
}
