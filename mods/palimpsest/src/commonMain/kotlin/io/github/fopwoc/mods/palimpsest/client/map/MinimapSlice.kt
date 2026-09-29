package io.github.fopwoc.mods.palimpsest.client.map

object MinimapSlice {
    private const val HEIGHT = 16

    fun ceiling(playerBlockY: Int): Int = Math.floorDiv(playerBlockY, HEIGHT) * HEIGHT + HEIGHT - 1
}
