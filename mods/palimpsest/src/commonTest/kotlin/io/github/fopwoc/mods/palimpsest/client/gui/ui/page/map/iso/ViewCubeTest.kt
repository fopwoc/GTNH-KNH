package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso

import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewCubeTest {
    private fun centreOf(cube: ViewCube, target: CubeTarget): Pair<Double, Double> {
        // Edges and corners span several faces; any of their patches will do.
        val patch = cube.patches.first { it.target == target }
        return patch.xs.average() to patch.ys.average()
    }

    @Test
    fun straightDownOnlyTheTopShowsAndItsMiddleIsTheTop() {
        val cube = ViewCube(0.0, IsoCamera.MAX_PITCH)
        assertEquals(9, cube.patches.size)
        assertEquals(CubeTarget(0, 1, 0), cube.targetAt(32.0, 32.0))
        assertNull(cube.targetAt(1.0, 1.0))
    }

    @Test
    fun fromTheSouthEastThreeFacesShowWithEveryPatchTheirOwn() {
        val cube = ViewCube(45.0, MapOrbit.ISOMETRIC_PITCH)
        assertEquals(27, cube.patches.size)
        for (target in
            listOf(
                CubeTarget(0, 1, 0),
                CubeTarget(0, 0, 1),
                CubeTarget(1, 0, 0),
                CubeTarget(1, 1, 1),
                CubeTarget(0, 1, 1),
                CubeTarget(1, -1, 1),
            )) {
            val (x, y) = centreOf(cube, target)
            assertEquals(target, cube.targetAt(x, y))
        }
    }

    @Test
    fun targetsLookFromWhereTheyPoint() {
        assertEquals(45.0, CubeTarget(1, 1, 1).yaw, 1e-9)
        assertEquals(MapOrbit.ISOMETRIC_PITCH, CubeTarget(1, 1, 1).pitch, 1e-3)
        assertEquals(180.0, CubeTarget(0, 1, -1).yaw, 1e-9)
        assertEquals(45.0, CubeTarget(0, 1, -1).pitch, 1e-9)
        assertEquals(IsoCamera.MIN_PITCH, CubeTarget(-1, 0, 0).pitch)
        assertTrue(CubeTarget(0, 1, 0).isTop)
    }
}
