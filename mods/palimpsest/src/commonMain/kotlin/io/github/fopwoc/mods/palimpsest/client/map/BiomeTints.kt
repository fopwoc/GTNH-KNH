package io.github.fopwoc.mods.palimpsest.client.map

/** RGB multipliers per biome id, white where the biome is unknown. */
class BiomeTints(val grass: (biome: Int) -> Int, val foliage: (biome: Int) -> Int, val water: (biome: Int) -> Int)
