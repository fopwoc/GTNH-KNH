package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.abs

class MeasurementGeometryTest {
    @Test
    fun snapToRightAngleKeepsOnlyDominantAxisDelta() {
        val snapped = MeasurementGeometry.snapToRightAngle(origin, diagonalCandidate)

        assertEquals(BlockSelection(x = 4, y = 64, z = 0, dimensionId = dimensionId), snapped)
    }

    @Test
    fun sphereRadiusUsesCenterToAnchorDistance() {
        val sphere = MeasurementGeometry.sphere(origin, sphereEdge)

        assertEquals(3.0, sphere.radius, 1.0E-6)
    }

    @Test
    fun containsSphereBlockMatchesDiscreteInterior() {
        assertTrue(MeasurementGeometry.containsSphereBlock(origin, sphereEdge, insideSphere))
        assertFalse(MeasurementGeometry.containsSphereBlock(origin, sphereEdge, outsideSphere))
    }

    @Test
    fun sphereLabelAnchorUsesVerticalRingForSideView() {
        val anchor = MeasurementGeometry.preferredSphereLabelAnchor(
            center = origin,
            edge = sphereEdge,
            eyeX = 10.5,
            eyeY = 64.5,
            eyeZ = 0.5
        )

        assertEquals(3.62, anchor[0], 1.0E-6)
        assertEquals(64.5, anchor[1], 1.0E-6)
        assertEquals(0.5, anchor[2], 1.0E-6)
    }

    @Test
    fun sphereLabelAnchorUsesHorizontalRingForTopView() {
        val anchor = MeasurementGeometry.preferredSphereLabelAnchor(
            center = origin,
            edge = sphereEdge,
            eyeX = 0.5,
            eyeY = 74.5,
            eyeZ = 0.5
        )

        assertEquals(0.5, anchor[0], 1.0E-6)
        assertEquals(67.62, anchor[1], 1.0E-6)
        assertEquals(0.5, anchor[2], 1.0E-6)
    }

    @Test
    fun sphereLabelAnchorStaysOnRenderedGreatCircle() {
        val anchor = MeasurementGeometry.preferredSphereLabelAnchor(
            center = origin,
            edge = sphereEdge,
            eyeX = 8.5,
            eyeY = 68.5,
            eyeZ = 7.5
        )

        val centerX = origin.centerX()
        val centerY = origin.centerY()
        val centerZ = origin.centerZ()
        val radiusWithOffset = 3.12
        val onXy = abs(anchor[2] - centerZ) <= 1.0E-6 && abs(planarDistance(anchor[0] - centerX, anchor[1] - centerY) - radiusWithOffset) <= 1.0E-6
        val onXz = abs(anchor[1] - centerY) <= 1.0E-6 && abs(planarDistance(anchor[0] - centerX, anchor[2] - centerZ) - radiusWithOffset) <= 1.0E-6
        val onYz = abs(anchor[0] - centerX) <= 1.0E-6 && abs(planarDistance(anchor[1] - centerY, anchor[2] - centerZ) - radiusWithOffset) <= 1.0E-6

        assertTrue(onXy || onXz || onYz)
    }

    private companion object {
        const val dimensionId = 0
        val origin = BlockSelection(x = 0, y = 64, z = 0, dimensionId = dimensionId)
        val diagonalCandidate = BlockSelection(x = 4, y = 66, z = 2, dimensionId = dimensionId)
        val sphereEdge = BlockSelection(x = 0, y = 67, z = 0, dimensionId = dimensionId)
        val insideSphere = BlockSelection(x = 1, y = 65, z = 1, dimensionId = dimensionId)
        val outsideSphere = BlockSelection(x = 3, y = 64, z = 1, dimensionId = dimensionId)
    }

    private fun planarDistance(a: Double, b: Double): Double = kotlin.math.sqrt(a * a + b * b)
}

