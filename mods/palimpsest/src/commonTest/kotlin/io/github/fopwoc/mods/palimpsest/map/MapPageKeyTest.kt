package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MapPageKeyTest {
    @Test
    fun reliefDependenciesFollowPageEdgesIncludingNegativeAndCoarseCoordinates() {
        assertEquals(
            setOf(MapPageKey(-1, -1, 0), MapPageKey(0, -1, 0), MapPageKey(-1, 0, 0)),
            MapPageKey.affectedBy(TileKey(-1, -1)).filter { it.lod == 0 }.toSet(),
        )
        assertEquals(
            setOf(MapPageKey(0, 0, 8), MapPageKey(1, 0, 8)),
            MapPageKey.affectedBy(TileKey(2032, 20)).filter { it.lod == 8 }.toSet(),
        )
        assertEquals(
            setOf(MapPageKey(0, 0, 8)),
            MapPageKey.affectedBy(TileKey(2031, 20)).filter { it.lod == 8 }.toSet(),
        )
    }
}
