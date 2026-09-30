package io.github.fopwoc.mods.palimpsest.client.map

import kotlin.math.ceil

/** Chooses the open space above the whole player, rather than an arbitrary chunk section. */
class MinimapSlice {
    private var selected: Int? = null
    private var candidate: Int? = null
    private var confirmations = 0

    fun ceiling(headY: Double, topY: Int, roofAt: (Int, Int, Int) -> Int?): Int {
        val head = ceil(headY).toInt().coerceAtMost(topY)
        val roofs = IntArray(9)
        var count = 0
        for (z in -1..1) for (x in -1..1) {
            val roof = roofAt(x, z, head)
            if (roof != null) roofs[count++] = roof
        }
        // Like cave-mode ceiling detection, a lone overhang is not an enclosed room.
        val target = if (count == roofs.size) roofs.min() - 1 else topY
        val current = selected
        if (current == null || current < head)
            return target.also {
                selected = it
                confirmations = 0
            }
        if (target == current) {
            candidate = null
            confirmations = 0
            return current
        }
        if (candidate != target) {
            candidate = target
            confirmations = 0
        }
        // Brief doorway/jump crossings should not reload the minimap.
        if (++confirmations >= 3) {
            selected = target
            confirmations = 0
        }
        return checkNotNull(selected)
    }
}
