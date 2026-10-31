package io.github.fopwoc.mods.framework.ui.compose.canvas

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpuShadowFrustumTest {
    /** A 64 × 64 block floor at y 64 from block (1000, -2000), one quad. */
    private val floor =
        GpuSceneMesh(
            1000.0,
            0.0,
            -2000.0,
            1,
            intArrayOf(
                GpuSceneMesh.vertex(0, 0, 64 * 16),
                GpuSceneMesh.vertex(64, 0, 64 * 16),
                GpuSceneMesh.vertex(64, 64, 64 * 16),
                GpuSceneMesh.vertex(0, 64, 64 * 16),
            ),
            IntArray(4) { GpuSceneMesh.color(0x808080, GpuSceneFace.UP) },
        )

    /** Straight down at the floor's middle, 2 pixels a block on a 256-pixel canvas. */
    private val camera =
        GpuSceneCamera(
            doubleArrayOf(
                2.0,
                0.0,
                0.0,
                128 - 2.0 * 1032,
                0.0,
                0.0,
                2.0,
                128 + 2.0 * 1968,
                0.0,
                -0.001,
                0.0,
                0.064,
            )
        )

    private fun clip(light: GpuSceneCamera, x: Double, y: Double, z: Double): List<Float> {
        val rows = light.relativeTo(x, y, z)
        return listOf(rows[3], rows[7], rows[11])
    }

    @Test
    fun everythingShownLandsInTheShadowMap() {
        val scene = GpuScene(listOf(floor), camera, light = GpuSceneLight(0.3, 1.0, 0.2))
        val light = assertNotNull(GpuShadowFrustum.of(scene, 256.0, 256.0))
        for (x in listOf(1000.0, 1064.0)) for (z in listOf(-2000.0, -1936.0)) {
            for (value in clip(light, x, 64.0, z)) assertTrue(
                value in -1.0001f..1.0001f,
                "$x $z $value",
            )
        }
    }

    @Test
    fun theLightLooksDownOnTheGround() {
        val scene = GpuScene(listOf(floor), camera, light = GpuSceneLight(0.0, 1.0, 0.0))
        val light = assertNotNull(GpuShadowFrustum.of(scene, 256.0, 256.0))
        // Higher is nearer the light: smaller depth.
        assertTrue(clip(light, 1032.0, 80.0, -1968.0)[2] < clip(light, 1032.0, 64.0, -1968.0)[2])
    }

    @Test
    fun aCanvasOverNothingCastsNothing() {
        val elsewhere =
            GpuSceneCamera(
                doubleArrayOf(
                    2.0,
                    0.0,
                    0.0,
                    128 - 2.0 * 9000,
                    0.0,
                    0.0,
                    2.0,
                    128.0,
                    0.0,
                    -0.001,
                    0.0,
                    0.064,
                )
            )
        assertNull(GpuShadowFrustum.of(GpuScene(listOf(floor), elsewhere), 256.0, 256.0))
    }
}
