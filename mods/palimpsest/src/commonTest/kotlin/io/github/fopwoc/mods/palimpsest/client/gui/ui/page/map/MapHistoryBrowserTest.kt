package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.map.TestHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapHistoryBrowserTest {
    private var nanos = 1_000_000_000L

    private fun TestHistory.commitAt(tick: Long, block: Int) {
        stage(0, 0, "block:$block")
        commit(tick)
    }

    /** The selected moment's tick, or null for live. */
    private val MapHistoryBrowser.tick: Long?
        get() = (time as? MapTime.At)?.tick

    private fun MapHistoryBrowser.settle(): Int {
        var frames = 0
        while (frames < 500) {
            nanos += FRAME_NANOS
            frames++
            if (!advance(nanos) && position == entry.toDouble()) return frames
        }
        error("strip never settled")
    }

    @Test
    fun stepsWalkFromLiveThroughSnapshotsAndBack() = withDb { db ->
        db.commitAt(100, 1)
        db.commitAt(200, 2)
        db.commitAt(300, 3)
        val history = MapHistoryBrowser(db.dimension.dimension)
        assertEquals(MapTime.Live, history.time)
        history.open()
        assertEquals(4, history.labels.size)
        history.step(1)
        assertEquals(300L, history.tick)
        assertEquals("Live", history.labels[0])
        history.step(2)
        assertEquals(100L, history.tick)
        history.step(5)
        assertEquals(100L, history.tick)
        history.step(-9)
        assertEquals(MapTime.Live, history.time)
        history.select(2)
        assertEquals(200L, history.tick)
        history.close()
        assertEquals(MapTime.Live, history.time)
        assertTrue(!history.open)
    }

    @Test
    fun newCommitsWhileBrowsingKeepTheSelectedSnapshot() = withDb { db ->
        db.commitAt(100, 1)
        db.commitAt(200, 2)
        val history = MapHistoryBrowser(db.dimension.dimension)
        history.open()
        history.step(2)
        assertEquals(100L, history.tick)
        db.commitAt(300, 3)
        history.advance(nanos)
        nanos += FRAME_NANOS
        history.advance(nanos)
        assertEquals(100L, history.tick)
        assertEquals(4, history.labels.size)
    }

    @Test
    fun thePositionGlidesToTheSelectionAndADragSnapsToTheNearestRow() = withDb { db ->
        for (i in 1..40) db.commitAt(i * 100L, i)
        val history = MapHistoryBrowser(db.dimension.dimension)
        history.open()
        history.advance(nanos)
        history.settle()
        assertEquals(0.0, history.position)

        history.step(10)
        nanos += FRAME_NANOS
        history.advance(nanos)
        assertTrue(history.position > 0.0 && history.position < 10.0)
        val frames = history.settle()
        assertTrue(frames in 3..80, "settled after $frames frames")
        assertEquals(10.0, history.position)
        assertEquals(3100L, history.tick)

        history.adopt(20.4)
        assertEquals(20, history.entry)
        assertEquals(20.4, history.position)
        history.settle()
        assertEquals(20.0, history.position)
        assertEquals(2100L, history.tick)
    }

    private inline fun withDb(test: (TestHistory) -> Unit) =
        TestHistory.with("palimpsest-history-", test)

    private companion object {
        const val FRAME_NANOS = 16_000_000L
    }
}
