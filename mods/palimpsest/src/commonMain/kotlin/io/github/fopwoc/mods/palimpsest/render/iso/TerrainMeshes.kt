package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh

/**
 * A [Heightfield]'s columns as meshes of [REGION] × [REGION] cells on a grid fixed to the world, so
 * the next field of a pan or a live refresh hands back the very mesh objects of regions that look
 * the same, and the GPU keeps them instead of uploading again. Not thread-safe: one owner meshes
 * one field at a time.
 */
class TerrainMeshes {
    private val logger = logger<TerrainMeshes>()

    private data class Key(val step: Int, val regionX: Int, val regionZ: Int)

    private class Entry(val hash: Long, val mesh: GpuSceneMesh)

    private var cache = HashMap<Key, Entry>()

    /** [field]'s regions, leaving out the columns [skip] names: something else draws those. */
    fun meshes(
        field: Heightfield,
        skip: (column: Int, row: Int) -> Boolean = NOTHING,
    ): List<GpuSceneMesh> {
        val step = field.step
        val floor = ColumnMesher.floor(field)
        val cellX = Math.floorDiv(field.originX, step)
        val cellZ = Math.floorDiv(field.originZ, step)
        val next = HashMap<Key, Entry>()
        var built = 0
        val regionsX =
            Math.floorDiv(cellX, REGION)..Math.floorDiv(cellX + field.columns - 1, REGION)
        val regionsZ = Math.floorDiv(cellZ, REGION)..Math.floorDiv(cellZ + field.rows - 1, REGION)
        for (regionZ in regionsZ) for (regionX in regionsX) {
            val column0 = regionX * REGION - cellX
            val row0 = regionZ * REGION - cellZ
            val hash = hash(field, floor, column0, row0, skip)
            if (hash == EMPTY) continue
            val key = Key(step, regionX, regionZ)
            val entry =
                cache[key]?.takeIf { it.hash == hash }
                    ?: Entry(
                            hash,
                            ColumnMesher.mesh(field, floor, column0, row0, REGION, REGION, skip),
                        )
                        .also { built++ }
            next[key] = entry
        }
        logger.debug("Meshed {} regions, {} rebuilt", next.size, built)
        cache = next
        return next.values.map { it.mesh }
    }

    /**
     * What a region's mesh depends on: its columns, the ring around them that its sides and shading
     * look at, which of its columns are left out, and the floor. [EMPTY] when nothing in it was
     * seen.
     */
    private fun hash(
        field: Heightfield,
        floor: Int,
        column0: Int,
        row0: Int,
        skip: (Int, Int) -> Boolean,
    ): Long {
        var seen = false
        var hash = FNV_OFFSET xor floor.toLong()
        for (row in row0 - 1..row0 + REGION) for (column in column0 - 1..column0 + REGION) {
            val present = field.present(column, row)
            val inside = row in row0 until row0 + REGION && column in column0 until column0 + REGION
            if (present && inside) seen = true
            if (present && inside && skip(column, row)) hash = (hash xor SKIPPED) * FNV_PRIME
            val at = row * field.columns + column
            hash = (hash xor (if (present) field.tops[at].toLong() else ABSENT)) * FNV_PRIME
            hash = (hash xor (if (present) field.colors[at].toLong() else 0L)) * FNV_PRIME
        }
        return if (seen) hash else EMPTY
    }

    private companion object {
        /** Cells a side; big enough to keep draw calls few, small enough to rebuild cheaply. */
        const val REGION = 32
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
        const val ABSENT = Long.MIN_VALUE
        const val EMPTY = 0L
        const val SKIPPED = 0x5EEDL
        val NOTHING: (Int, Int) -> Boolean = { _, _ -> false }
    }
}
