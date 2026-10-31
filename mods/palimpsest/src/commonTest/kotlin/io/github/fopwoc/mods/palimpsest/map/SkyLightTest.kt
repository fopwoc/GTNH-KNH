package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneFace
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SkyLightTest {
    @Test
    fun theDayCycleTurnsTheSunAsVanillaDoes() {
        assertEquals(0.0, SkyLight.celestialAngle(6000), 1e-9)
        assertEquals(0.5, SkyLight.celestialAngle(18000), 1e-9)
        assertEquals(0.0, SkyLight.celestialAngle(6000 + 24000 * 3L), 1e-9)
        // Sunrise at tick 0 sits a little past three quarters, as vanilla eases the turn.
        assertTrue(SkyLight.celestialAngle(0) in 0.75..0.8)
    }

    @Test
    fun noonIsAWhiteSunOverheadAndMidnightADimCoolMoon() {
        val noon = SkyLight.at(0.0)
        assertTrue(noon.y > 0.9f)
        assertEquals(1f, noon.intensity)
        assertEquals(0xFFFFFF, noon.color)
        val midnight = SkyLight.at(0.5)
        assertTrue(midnight.y > 0.9f)
        assertTrue(midnight.intensity < 0.5f)
        assertTrue(
            (midnight.color and 0xFF) > (midnight.color shr 16 and 0xFF),
            "moonlight is blue",
        )
        assertTrue(
            midnight.brightness(GpuSceneFace.UP) < noon.brightness(GpuSceneFace.UP),
            "night is darker",
        )
    }

    @Test
    fun dawnIsWarmAndLowFromTheEast() {
        val dawn = SkyLight.at(SkyLight.celestialAngle(500))
        assertTrue(dawn.x > 0.5f, "the sun rises in the east")
        assertTrue((dawn.color and 0xFF) < (dawn.color shr 16 and 0xFF), "dawn is warm")
        assertTrue(dawn.brightness(GpuSceneFace.EAST) > dawn.brightness(GpuSceneFace.WEST))
    }

    @Test
    fun noSkyMeansTheFixedLight() {
        assertSame(GpuSceneLight.FIXED, SkyLight.at(null))
    }
}
