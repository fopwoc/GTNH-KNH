package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlinx.serialization.Serializable

@Serializable
data class PersistedMeasurement(
    val mode: MeasurementMode,
    val first: BlockSelection,
    val second: BlockSelection,
) {
    fun containsAnchor(anchor: BlockSelection): Boolean = first == anchor || second == anchor

    fun offset(deltaX: Int, deltaY: Int, deltaZ: Int): PersistedMeasurement =
        copy(
            first = first.offset(deltaX, deltaY, deltaZ),
            second = second.offset(deltaX, deltaY, deltaZ),
        )
}

internal fun PersistedMeasurement.key(): String {
    if (mode == MeasurementMode.SPHERE) {
        return buildString {
            append(mode.name)
            append('|')
            append(first.dimensionId)
            append(':')
            append(first.x)
            append(',')
            append(first.y)
            append(',')
            append(first.z)
            append('|')
            append(MeasurementGeometry.sphereRadiusSquared(first, second))
        }
    }

    val orderedAnchors = listOf(first, second).sortedWith(blockSelectionComparator)
    return buildString {
        append(mode.name)
        orderedAnchors.forEach { anchor ->
            append('|')
            append(anchor.dimensionId)
            append(':')
            append(anchor.x)
            append(',')
            append(anchor.y)
            append(',')
            append(anchor.z)
        }
    }
}
