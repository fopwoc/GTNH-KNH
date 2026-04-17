package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

data class AreaMeasurement(
    val xLength: Int,
    val yLength: Int,
    val zLength: Int
) {
    val volume: Long = xLength.toLong() * yLength.toLong() * zLength.toLong()

    val label: String = "${xLength}x${yLength}x${zLength} (${volume})"
}

object MeasurementGeometry {
    fun lineDistance(first: BlockSelection, second: BlockSelection): Double =
        directDistance(first, second) + 1.0

    fun formatDistance(distance: Double): String = String.format(Locale.US, "%.2f", distance)

    fun area(first: BlockSelection, second: BlockSelection): AreaMeasurement = AreaMeasurement(
        xLength = axisLength(first.x, second.x),
        yLength = axisLength(first.y, second.y),
        zLength = axisLength(first.z, second.z)
    )

    fun closestPointOnSegment(
        first: BlockSelection,
        second: BlockSelection,
        pointX: Double,
        pointY: Double,
        pointZ: Double
    ): DoubleArray = closestPointOnSegmentCoordinates(
        startX = first.centerX(),
        startY = first.centerY(),
        startZ = first.centerZ(),
        endX = second.centerX(),
        endY = second.centerY(),
        endZ = second.centerZ(),
        pointX = pointX,
        pointY = pointY,
        pointZ = pointZ,
        minClamp = 0.08,
        maxClamp = 0.92
    )

    fun preferredAreaLabelAnchor(
        first: BlockSelection,
        second: BlockSelection,
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double
    ): DoubleArray {
        val minX = minOf(first.x, second.x).toDouble()
        val minY = minOf(first.y, second.y).toDouble()
        val minZ = minOf(first.z, second.z).toDouble()
        val maxX = maxOf(first.x, second.x).toDouble() + 1.0
        val maxY = maxOf(first.y, second.y).toDouble() + 1.0
        val maxZ = maxOf(first.z, second.z).toDouble() + 1.0
        return preferredAreaLabelAnchor(minX, minY, minZ, maxX, maxY, maxZ, eyeX, eyeY, eyeZ)
    }

    private fun directDistance(first: BlockSelection, second: BlockSelection): Double {
        val dx = second.centerX() - first.centerX()
        val dy = second.centerY() - first.centerY()
        val dz = second.centerZ() - first.centerZ()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun preferredAreaLabelAnchor(
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double
    ): DoubleArray {
        val edges = arrayOf(
            doubleArrayOf(minX, minY, minZ, maxX, minY, minZ),
            doubleArrayOf(maxX, minY, minZ, maxX, minY, maxZ),
            doubleArrayOf(maxX, minY, maxZ, minX, minY, maxZ),
            doubleArrayOf(minX, minY, maxZ, minX, minY, minZ),
            doubleArrayOf(minX, maxY, minZ, maxX, maxY, minZ),
            doubleArrayOf(maxX, maxY, minZ, maxX, maxY, maxZ),
            doubleArrayOf(maxX, maxY, maxZ, minX, maxY, maxZ),
            doubleArrayOf(minX, maxY, maxZ, minX, maxY, minZ),
            doubleArrayOf(minX, minY, minZ, minX, maxY, minZ),
            doubleArrayOf(maxX, minY, minZ, maxX, maxY, minZ),
            doubleArrayOf(maxX, minY, maxZ, maxX, maxY, maxZ),
            doubleArrayOf(minX, minY, maxZ, minX, maxY, maxZ)
        )

        var bestPoint = doubleArrayOf((minX + maxX) * 0.5, maxY, (minZ + maxZ) * 0.5)
        var bestDistanceSquared = Double.MAX_VALUE
        for (edge in edges) {
            val candidate = closestPointOnSegmentCoordinates(
                startX = edge[0],
                startY = edge[1],
                startZ = edge[2],
                endX = edge[3],
                endY = edge[4],
                endZ = edge[5],
                pointX = eyeX,
                pointY = eyeY,
                pointZ = eyeZ,
                minClamp = 0.0,
                maxClamp = 1.0
            )
            val dx = candidate[0] - eyeX
            val dy = candidate[1] - eyeY
            val dz = candidate[2] - eyeZ
            val distanceSquared = dx * dx + dy * dy + dz * dz
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared
                bestPoint = candidate
            }
        }

        val centerX = (minX + maxX) * 0.5
        val centerY = (minY + maxY) * 0.5
        val centerZ = (minZ + maxZ) * 0.5
        val offset = 0.12
        return doubleArrayOf(
            bestPoint[0] + sign(bestPoint[0] - centerX) * offset,
            bestPoint[1] + sign(bestPoint[1] - centerY) * offset,
            bestPoint[2] + sign(bestPoint[2] - centerZ) * offset
        )
    }

    private fun closestPointOnSegmentCoordinates(
        startX: Double,
        startY: Double,
        startZ: Double,
        endX: Double,
        endY: Double,
        endZ: Double,
        pointX: Double,
        pointY: Double,
        pointZ: Double,
        minClamp: Double,
        maxClamp: Double
    ): DoubleArray {
        val dx = endX - startX
        val dy = endY - startY
        val dz = endZ - startZ
        val lengthSquared = dx * dx + dy * dy + dz * dz
        if (lengthSquared <= 1.0E-6) {
            return doubleArrayOf(startX, startY, startZ)
        }

        val projection = ((pointX - startX) * dx + (pointY - startY) * dy + (pointZ - startZ) * dz) / lengthSquared
        val clamped = projection.coerceIn(minClamp, maxClamp)
        return doubleArrayOf(
            startX + dx * clamped,
            startY + dy * clamped,
            startZ + dz * clamped
        )
    }

    private fun axisLength(a: Int, b: Int): Int = abs(a - b) + 1
}

