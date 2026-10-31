package io.github.fopwoc.mods.framework.ui.compose.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GpuSceneMeshTest {
    @Test
    fun shadingKeepsItsLeanEitherWayAndItsSky() {
        val shading = GpuSceneMesh.shading(-0.4f, 0.3f, 0.6f)
        assertEquals(-0.4f, GpuSceneMesh.leanX(shading), 0.01f)
        assertEquals(0.3f, GpuSceneMesh.leanZ(shading), 0.01f)
        assertEquals(0.6f, GpuSceneMesh.occlusion(shading), 0.01f)
        assertEquals(GpuSceneMesh.OPEN, GpuSceneMesh.shading(0f, 0f))
    }

    @Test
    fun aHollowIsDarkerThanTheOpen() {
        val light = GpuSceneLight(0.3, 1.0, 0.2)
        assertTrue(light.brightness(0f, 1f, 0f, 0.5f) < light.brightness(0f, 1f, 0f))
    }
}
