package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.render.GlassGrid
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

/** World-space drawing primitives supplied by each Minecraft renderer. */
interface MeasurementWorldCanvas {
    val eyeX: Double
    val eyeY: Double
    val eyeZ: Double

    fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, color: Color, width: Float)

    fun blockOutline(x: Int, y: Int, z: Int, color: Color, width: Float)

    fun cornerBrackets(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Color, width: Float, arm: Double, grow: Double,
    )

    fun filledBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Color)

    fun glassBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Color)

    fun glassSphere(centerX: Double, centerY: Double, centerZ: Double, radius: Double, color: Color, grid: GlassGrid)

    fun label(x: Double, y: Double, z: Double, text: String, color: Color)

    fun ghosted(draw: (hiddenAlpha: Int?) -> Unit)
}
