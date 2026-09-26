package io.github.fopwoc.mods.palimpsest.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapCameraTest {
    @Test
    fun largeViewportsTakeACoarserLevelInsteadOfTooManyPages() {
        // GUI scale 1 on a 1080p screen, with pages just over 64 GUI pixels wide.
        val camera = MapCamera(0.0, 0.0, 0.51, 1920, 1080)
        assertEquals(1, camera.lod)
        val pages = camera.visiblePages()
        assertTrue(pages.size in 1..256)
        assertTrue(pages.all { it.lod == 1 })
    }

    @Test
    fun pastTheCoarsestLevelThePagesNearestTheCentreAreKept() {
        val camera = MapCamera(0.0, 0.0, 1.0 / 8192, 1_000_000, 1_000_000)
        assertEquals(MapPageKey.MAX_LOD, camera.lod)
        val pages = camera.visiblePages()
        assertEquals(256, pages.size)
        assertTrue(MapPageKey(0, 0, MapPageKey.MAX_LOD) in pages)
        assertTrue(MapPageKey(-1, -1, MapPageKey.MAX_LOD) in pages)
    }
}
