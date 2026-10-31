package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.palimpsest.render.TerrainShader

/**
 * How history blocks look in the voxel view: [kinds] per block id raw as [VoxelKind] ordinals, with
 * [SHINY] added for a polished block, [drawn] the map's colour id per raw, coloured by [shader].
 * Raws past either array read as empty.
 */
class VoxelPalette(
    private val kinds: ByteArray,
    private val drawn: IntArray,
    private val shader: TerrainShader,
) {
    fun kind(raw: Int): VoxelKind =
        if (raw in kinds.indices) KINDS[kinds[raw].toInt() and KIND_MASK] else VoxelKind.EMPTY

    /** Whether the block mirrors the light: metal, gems, ice. */
    fun isShiny(raw: Int): Boolean = raw in kinds.indices && kinds[raw].toInt() and SHINY != 0

    /** The block's own colour in [biome], unlit; 0 for none. */
    fun color(raw: Int, biome: Int): Int = shader.column(drawnId(raw), 0, biome)

    /** [depth] blocks of [biome]'s water, its alpha how much of the floor under it it hides. */
    fun water(depth: Int, biome: Int): Int = shader.water(depth, biome)

    private fun drawnId(raw: Int): Int = if (raw in drawn.indices) drawn[raw] else 0

    companion object {
        /** Added to a kind's ordinal for a shiny block. */
        const val SHINY = 0x10
        private const val KIND_MASK = 0x0F
        private val KINDS = VoxelKind.entries.toTypedArray()
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
