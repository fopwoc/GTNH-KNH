package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlinx.serialization.Serializable

@Serializable(with = BlockSelectionSerializer::class)
data class BlockSelection(
    val x: Int,
    val y: Int,
    val z: Int,
    val dimensionId: String,
) {
    fun centerX(): Double = x + 0.5

    fun centerY(): Double = y + 0.5

    fun centerZ(): Double = z + 0.5

    fun isInDimension(targetDimensionId: String): Boolean = dimensionId == targetDimensionId

    fun offset(deltaX: Int, deltaY: Int, deltaZ: Int): BlockSelection =
        copy(
            x = x + deltaX,
            y = y + deltaY,
            z = z + deltaZ,
        )
}

internal val blockSelectionComparator =
    compareBy<BlockSelection>(
        BlockSelection::dimensionId,
        BlockSelection::x,
        BlockSelection::y,
        BlockSelection::z,
    )
