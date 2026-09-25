package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.render.GlassGrid
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementOverlayPainterTest {
    @AfterTest fun reset() = MeasurementSelectionState.resetAll()

    @Test
    fun hiddenGuiKeepsShapeButOmitsHandlesAndLabels() {
        MeasurementSelectionState.replacePersistedMeasurements(
            listOf(
                PersistedMeasurement(
                    MeasurementMode.LINE,
                    BlockSelection(0, 64, 0, "minecraft:overworld"),
                    BlockSelection(3, 64, 0, "minecraft:overworld"),
                )
            )
        )
        val canvas = RecordingCanvas()

        MeasurementOverlayPainter.paint(
            canvas = canvas,
            currentDimensionId = "minecraft:overworld",
            active = true,
            hoveredTarget = null,
            hideGui = true,
            targetModifierDown = false,
        )

        assertEquals(1, canvas.lines)
        assertEquals(0, canvas.handles)
        assertEquals(0, canvas.labels)
    }

    private class RecordingCanvas : MeasurementWorldCanvas {
        override val eyeX = 0.0
        override val eyeY = 64.0
        override val eyeZ = 0.0
        var lines = 0
        var handles = 0
        var labels = 0

        override fun line(
            x1: Double,
            y1: Double,
            z1: Double,
            x2: Double,
            y2: Double,
            z2: Double,
            color: Color,
            width: Float,
        ) {
            lines++
        }

        override fun blockOutline(x: Int, y: Int, z: Int, color: Color, width: Float) = Unit

        override fun cornerBrackets(
            minX: Double,
            minY: Double,
            minZ: Double,
            maxX: Double,
            maxY: Double,
            maxZ: Double,
            color: Color,
            width: Float,
            arm: Double,
            grow: Double,
        ) {
            handles++
        }

        override fun filledBox(
            minX: Double,
            minY: Double,
            minZ: Double,
            maxX: Double,
            maxY: Double,
            maxZ: Double,
            color: Color,
        ) = Unit

        override fun glassBox(
            minX: Double,
            minY: Double,
            minZ: Double,
            maxX: Double,
            maxY: Double,
            maxZ: Double,
            color: Color,
        ) = Unit

        override fun glassSphere(
            centerX: Double,
            centerY: Double,
            centerZ: Double,
            radius: Double,
            color: Color,
            grid: GlassGrid,
        ) = Unit

        override fun label(x: Double, y: Double, z: Double, text: String, color: Color) {
            labels++
        }

        override fun ghosted(draw: (Int?) -> Unit) = draw(null)
    }
}
