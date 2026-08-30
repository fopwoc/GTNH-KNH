package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

data class AreaMeasurement(
    val xLength: Int,
    val yLength: Int,
    val zLength: Int,
) {
  val volume: Long = xLength.toLong() * yLength.toLong() * zLength.toLong()

  val label: String = "${xLength}x${yLength}x${zLength} (${volume})"
}

data class SphereMeasurement(val radius: Double) {
  val label: String = "r=${String.format(Locale.US, "%.2f", radius)} blocks"
}

object MeasurementGeometry {
  fun lineDistance(first: BlockSelection, second: BlockSelection): Double =
      directDistance(first, second) + 1.0

  fun formatDistance(distance: Double): String = String.format(Locale.US, "%.2f", distance)

  fun area(first: BlockSelection, second: BlockSelection): AreaMeasurement =
      AreaMeasurement(
          xLength = axisLength(first.x, second.x),
          yLength = axisLength(first.y, second.y),
          zLength = axisLength(first.z, second.z),
      )

  fun sphere(center: BlockSelection, edge: BlockSelection): SphereMeasurement =
      SphereMeasurement(radius = sphereRadius(center, edge))

  fun sphereRadius(center: BlockSelection, edge: BlockSelection): Double =
      sqrt(sphereRadiusSquared(center, edge).toDouble())

  fun sphereRadiusSquared(center: BlockSelection, edge: BlockSelection): Int {
    val dx = edge.x - center.x
    val dy = edge.y - center.y
    val dz = edge.z - center.z
    return dx * dx + dy * dy + dz * dz
  }

  fun containsSphereBlock(
      center: BlockSelection,
      edge: BlockSelection,
      block: BlockSelection,
  ): Boolean {
    if (center.dimensionId != edge.dimensionId || center.dimensionId != block.dimensionId) {
      return false
    }

    val dx = block.x - center.x
    val dy = block.y - center.y
    val dz = block.z - center.z
    return dx * dx + dy * dy + dz * dz <= sphereRadiusSquared(center, edge)
  }

  fun snapToRightAngle(origin: BlockSelection, candidate: BlockSelection): BlockSelection {
    if (origin.dimensionId != candidate.dimensionId) {
      return candidate
    }

    val deltaX = abs(candidate.x - origin.x)
    val deltaY = abs(candidate.y - origin.y)
    val deltaZ = abs(candidate.z - origin.z)
    return when {
      deltaX >= deltaY && deltaX >= deltaZ -> candidate.copy(y = origin.y, z = origin.z)
      deltaY >= deltaZ -> candidate.copy(x = origin.x, z = origin.z)
      else -> candidate.copy(x = origin.x, y = origin.y)
    }
  }

  fun closestPointOnSegment(
      first: BlockSelection,
      second: BlockSelection,
      pointX: Double,
      pointY: Double,
      pointZ: Double,
  ): DoubleArray =
      closestPointOnSegmentCoordinates(
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
          maxClamp = 0.92,
      )

  fun preferredAreaLabelAnchor(
      first: BlockSelection,
      second: BlockSelection,
      eyeX: Double,
      eyeY: Double,
      eyeZ: Double,
  ): DoubleArray {
    val minX = minOf(first.x, second.x).toDouble()
    val minY = minOf(first.y, second.y).toDouble()
    val minZ = minOf(first.z, second.z).toDouble()
    val maxX = maxOf(first.x, second.x).toDouble() + 1.0
    val maxY = maxOf(first.y, second.y).toDouble() + 1.0
    val maxZ = maxOf(first.z, second.z).toDouble() + 1.0
    return preferredAreaLabelAnchor(minX, minY, minZ, maxX, maxY, maxZ, eyeX, eyeY, eyeZ)
  }

  fun preferredSphereLabelAnchor(
      center: BlockSelection,
      edge: BlockSelection,
      eyeX: Double,
      eyeY: Double,
      eyeZ: Double,
  ): DoubleArray {
    val radius = sphereRadius(center, edge)
    val centerX = center.centerX()
    val centerY = center.centerY()
    val centerZ = center.centerZ()
    if (radius <= 1.0E-6) {
      return doubleArrayOf(centerX, centerY, centerZ)
    }

    val ringCandidates =
        arrayOf(
            closestPointOnCircle(
                centerX,
                centerY,
                centerZ,
                radius,
                eyeX,
                eyeY,
                eyeZ,
                SphereCirclePlane.XY,
            ),
            closestPointOnCircle(
                centerX,
                centerY,
                centerZ,
                radius,
                eyeX,
                eyeY,
                eyeZ,
                SphereCirclePlane.XZ,
            ),
            closestPointOnCircle(
                centerX,
                centerY,
                centerZ,
                radius,
                eyeX,
                eyeY,
                eyeZ,
                SphereCirclePlane.YZ,
            ),
        )

    val bestPoint =
        ringCandidates.minByOrNull { candidate ->
          val dx = candidate[0] - eyeX
          val dy = candidate[1] - eyeY
          val dz = candidate[2] - eyeZ
          dx * dx + dy * dy + dz * dz
        } ?: return doubleArrayOf(centerX, centerY + radius + 0.12, centerZ)

    val radialX = bestPoint[0] - centerX
    val radialY = bestPoint[1] - centerY
    val radialZ = bestPoint[2] - centerZ
    val radialLength = sqrt(radialX * radialX + radialY * radialY + radialZ * radialZ)
    if (radialLength <= 1.0E-6) {
      return doubleArrayOf(centerX, centerY + radius + 0.12, centerZ)
    }

    return doubleArrayOf(
        centerX + radialX * ((radius + 0.12) / radialLength),
        centerY + radialY * ((radius + 0.12) / radialLength),
        centerZ + radialZ * ((radius + 0.12) / radialLength),
    )
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
      eyeZ: Double,
  ): DoubleArray {
    val edges =
        arrayOf(
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
            doubleArrayOf(minX, minY, maxZ, minX, maxY, maxZ),
        )

    var bestPoint = doubleArrayOf((minX + maxX) * 0.5, maxY, (minZ + maxZ) * 0.5)
    var bestDistanceSquared = Double.MAX_VALUE
    for (edge in edges) {
      val candidate =
          closestPointOnSegmentCoordinates(
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
              maxClamp = 1.0,
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
        bestPoint[2] + sign(bestPoint[2] - centerZ) * offset,
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
      maxClamp: Double,
  ): DoubleArray {
    val dx = endX - startX
    val dy = endY - startY
    val dz = endZ - startZ
    val lengthSquared = dx * dx + dy * dy + dz * dz
    if (lengthSquared <= 1.0E-6) {
      return doubleArrayOf(startX, startY, startZ)
    }

    val projection =
        ((pointX - startX) * dx + (pointY - startY) * dy + (pointZ - startZ) * dz) / lengthSquared
    val clamped = projection.coerceIn(minClamp, maxClamp)
    return doubleArrayOf(
        startX + dx * clamped,
        startY + dy * clamped,
        startZ + dz * clamped,
    )
  }

  private fun closestPointOnCircle(
      centerX: Double,
      centerY: Double,
      centerZ: Double,
      radius: Double,
      pointX: Double,
      pointY: Double,
      pointZ: Double,
      plane: SphereCirclePlane,
  ): DoubleArray {
    val offsetA: Double
    val offsetB: Double
    val fallback = doubleArrayOf(centerX, centerY + radius, centerZ)
    when (plane) {
      SphereCirclePlane.XY -> {
        offsetA = pointX - centerX
        offsetB = pointY - centerY
      }
      SphereCirclePlane.XZ -> {
        offsetA = pointX - centerX
        offsetB = pointZ - centerZ
      }
      SphereCirclePlane.YZ -> {
        offsetA = pointY - centerY
        offsetB = pointZ - centerZ
      }
    }

    val planarLength = sqrt(offsetA * offsetA + offsetB * offsetB)
    if (planarLength <= 1.0E-6) {
      return when (plane) {
        SphereCirclePlane.XY -> fallback
        SphereCirclePlane.XZ -> doubleArrayOf(centerX, centerY, centerZ + radius)
        SphereCirclePlane.YZ -> doubleArrayOf(centerX, centerY + radius, centerZ)
      }
    }

    val scaledA = offsetA * (radius / planarLength)
    val scaledB = offsetB * (radius / planarLength)
    return when (plane) {
      SphereCirclePlane.XY -> doubleArrayOf(centerX + scaledA, centerY + scaledB, centerZ)
      SphereCirclePlane.XZ -> doubleArrayOf(centerX + scaledA, centerY, centerZ + scaledB)
      SphereCirclePlane.YZ -> doubleArrayOf(centerX, centerY + scaledA, centerZ + scaledB)
    }
  }

  private fun axisLength(a: Int, b: Int): Int = abs(a - b) + 1
}

private enum class SphereCirclePlane {
  XY,
  XZ,
  YZ,
}
