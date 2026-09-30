package io.github.fopwoc.mods.palimpsest.client.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MinimapSliceTest {
    @Test
    fun outdoorsDoesNotReloadAtSectionBoundaries() {
        val slice = MinimapSlice()
        for (head in listOf(-14.2, 15.8, 16.8, 31.8, 65.8, 145.8)) {
            assertEquals(255, slice.ceiling(head, 255) { _, _, _ -> null })
        }
    }

    @Test
    fun roomsShowTheFreeSpaceAboveTheWholePlayer() {
        val slice = MinimapSlice()
        for (head in listOf(62.8, 64.8, 70.8, 78.8)) {
            assertEquals(
                79,
                slice.ceiling(head, 255) { _, _, from ->
                    assertEquals(kotlin.math.ceil(head).toInt(), from)
                    80
                },
            )
        }
    }

    @Test
    fun briefDoorwayCrossingsAndLoneOverhangsDoNotChangeLayer() {
        val slice = MinimapSlice()
        assertEquals(
            255,
            slice.ceiling(62.8, 255) { x, z, _ -> if (x == 0 && z == 0) 70 else null },
        )
        repeat(2) { assertEquals(255, slice.ceiling(62.8, 255) { _, _, _ -> 80 }) }
        assertEquals(255, slice.ceiling(62.8, 255) { _, _, _ -> null })
        repeat(2) { assertEquals(255, slice.ceiling(62.8, 255) { _, _, _ -> 80 }) }
        assertEquals(79, slice.ceiling(62.8, 255) { _, _, _ -> 80 })
        // Ascending beyond the previous room bypasses the debounce.
        assertEquals(99, slice.ceiling(82.8, 255) { _, _, _ -> 100 })
    }

    @Test
    fun surroundingStructuresDoNotLowerThePlayersRoomCeiling() {
        assertEquals(
            79,
            MinimapSlice().ceiling(64.8, 255) { x, z, _ ->
                if (x == 0 && z == 0) 80 else 66
            },
        )
    }
}
