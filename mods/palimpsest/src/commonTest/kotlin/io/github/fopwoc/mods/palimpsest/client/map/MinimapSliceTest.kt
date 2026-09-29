package io.github.fopwoc.mods.palimpsest.client.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MinimapSliceTest {
    @Test
    fun ceilingChangesOnlyAtSixteenBlockBoundaries() {
        mapOf(
                -17 to -17,
                -16 to -1,
                -1 to -1,
                0 to 15,
                15 to 15,
                16 to 31,
                31 to 31,
                32 to 47,
            )
            .forEach { (playerY, ceiling) ->
                assertEquals(ceiling, MinimapSlice.ceiling(playerY), "player Y $playerY")
            }
    }
}
