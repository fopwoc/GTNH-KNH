package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapViewStateTest {
    private val width = 400
    private val height = 300
    private var nanos = 1_000_000_000L

    private fun MapViewState.settle(): Int {
        var frames = 0
        advance(nanos, width, height)
        while (frames < 1000) {
            nanos += FRAME_NANOS
            frames++
            if (!advance(nanos, width, height)) return frames
        }
        error("camera never settled")
    }

    @Test
    fun wheelZoomEasesInAndKeepsTheBlockUnderTheCursor() {
        val state = MapViewState(100.0, 200.0)
        val atX = 50.0
        val atY = 250.0
        val worldX = state.centerX + (atX - width / 2.0) / state.pixelsPerBlock
        val worldZ = state.centerZ + (atY - height / 2.0) / state.pixelsPerBlock

        state.zoomBy(3.0, atX, atY)
        state.advance(nanos, width, height)
        nanos += FRAME_NANOS
        assertTrue(state.advance(nanos, width, height))
        val midway = state.pixelsPerBlock
        assertTrue(midway > 1.0 && midway < MapViewState.ZOOM_STEP.pow(3))
        assertEquals(worldX, state.centerX + (atX - width / 2.0) / midway, 1e-9)
        assertEquals(worldZ, state.centerZ + (atY - height / 2.0) / midway, 1e-9)

        val frames = state.settle()
        assertTrue(frames in 5..60, "settled after $frames frames")
        assertEquals(MapViewState.ZOOM_STEP.pow(3), state.pixelsPerBlock, 1e-9)
        assertEquals(worldX, state.centerX + (atX - width / 2.0) / state.pixelsPerBlock, 1e-9)
        assertEquals(worldZ, state.centerZ + (atY - height / 2.0) / state.pixelsPerBlock, 1e-9)
    }

    @Test
    fun trackpadFractionsAccumulateAndClampToTheZoomRange() {
        val state = MapViewState(0.0, 0.0)
        repeat(4) { state.zoomBy(0.25, 0.0, 0.0) }
        state.settle()
        assertEquals(MapViewState.ZOOM_STEP, state.pixelsPerBlock, 1e-9)

        repeat(200) { state.zoomBy(1.0, 0.0, 0.0) }
        state.settle()
        assertEquals(MapViewState.MAX_PIXELS_PER_BLOCK, state.pixelsPerBlock, 1e-9)
    }

    @Test
    fun dragFollowsThePointerAndFlingsOnRelease() {
        val state = MapViewState(0.0, 0.0)
        state.advance(nanos, width, height)
        state.dragBy(0.0, 0.0, nanos)
        repeat(5) {
            nanos += FRAME_NANOS
            state.dragBy(10.0, 0.0, nanos)
            assertEquals(-10.0 * (it + 1), state.centerX, 1e-9)
            assertFalse(state.advance(nanos, width, height))
        }
        state.endDrag(nanos)
        val released = state.centerX
        val frames = state.settle()
        assertTrue(state.centerX < released - 10.0, "fling carried on to ${state.centerX}")
        assertTrue(frames in 5..200, "fling lasted $frames frames")
    }

    @Test
    fun releaseAfterAPauseDoesNotFling() {
        val state = MapViewState(0.0, 0.0)
        state.advance(nanos, width, height)
        state.dragBy(0.0, 0.0, nanos)
        nanos += FRAME_NANOS
        state.dragBy(40.0, 0.0, nanos)
        nanos += 500_000_000L
        state.endDrag(nanos)
        val released = state.centerX
        state.settle()
        assertEquals(released, state.centerX, 1e-9)
    }

    @Test
    fun keyPanAndLookAtEaseTowardTheTarget() {
        val state = MapViewState(0.0, 0.0)
        state.advance(nanos, width, height)
        state.panBy(-64.0, 0.0)
        nanos += FRAME_NANOS
        state.advance(nanos, width, height)
        assertTrue(state.centerX > 0.0 && state.centerX < 64.0)
        state.settle()
        assertEquals(64.0, state.centerX, 1e-9)

        state.lookAt(-500.0, 700.0)
        state.settle()
        assertEquals(-500.0, state.centerX, 1e-9)
        assertEquals(700.0, state.centerZ, 1e-9)
        assertTrue(abs(state.pixelsPerBlock - 1.0) < 1e-9)
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
    }
}
