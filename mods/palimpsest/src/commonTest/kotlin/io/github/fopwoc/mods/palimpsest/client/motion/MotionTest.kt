package io.github.fopwoc.mods.palimpsest.client.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    @Test
    fun theFirstFrameTakesNoTimeAndStallsAreClamped() {
        val clock = FrameClock(maxSeconds = 0.1)
        assertEquals(0.0, clock.tick(5_000_000_000))
        assertEquals(0.016, clock.tick(5_016_000_000), 1e-9)
        assertEquals(0.1, clock.tick(9_000_000_000))
    }

    @Test
    fun aGlidingPointEasesAfterSmallStepsAndJumpsOnTeleports() {
        val point = GlidingPoint(0.0, 64.0, 0.0)
        point.follow(1.0, 64.0, 0.0, 0.016)
        assertTrue(point.x > 0.0 && point.x < 1.0)
        point.follow(1.0, 64.0, 0.0, 10.0)
        assertEquals(1.0, point.x, 1e-9)
        point.follow(500.0, 64.0, -3.0, 0.016)
        assertEquals(500.0, point.x)
        assertEquals(-3.0, point.z)
    }
}
