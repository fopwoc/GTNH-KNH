package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.palimpsest.render.TerrainShader

/**
 * How history blocks look in the voxel view: [kinds] per block id raw as [VoxelKind] ordinals,
 * [drawn] the map's colour id per raw, coloured by [shader]. Raws past either array read as empty.
 */
class VoxelPalette(
    private val kinds: ByteArray,
    private val drawn: IntArray,
    private val shader: TerrainShader,
) {
    fun kind(raw: Int): VoxelKind =
        if (raw in kinds.indices) KINDS[kinds[raw].toInt()] else VoxelKind.EMPTY

    /** The block's own colour in [biome], unlit; 0 for none. */
    fun color(raw: Int, biome: Int): Int = shader.column(drawnId(raw), 0, biome)

    /**
     * [depth] blocks of [biome]'s water over [floor], as the map draws a lake from above; over no
     * floor at all, over the [water] block itself.
     */
    fun water(floor: Int, water: Int, depth: Int, biome: Int): Int =
        shader.column(drawnId(floor).takeIf { it != 0 } ?: drawnId(water), depth, biome)

    private fun drawnId(raw: Int): Int = if (raw in drawn.indices) drawn[raw] else 0

    private companion object {
        val KINDS = VoxelKind.entries.toTypedArray()
    }
}

/** What a block is to the voxel view. */
enum class VoxelKind {
    /** Air, glass, grass: looked through. */
    EMPTY,
    /** A full block. */
    SOLID,
    /** Water: only its surface against empty blocks shows, coloured over its floor. */
    WATER,
    /** A torch, a flower, a pipe: a thin decal on what it stands on. */
    DECAL,
}
