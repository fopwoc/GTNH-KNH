package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.map.TestBlocks
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapHistoryBrowserTest {
    private var nanos = 1_000_000_000L

    private fun MapTree.commitAt(epoch: Long, block: Int) =
        commit(
            epoch,
            mapOf(TileKey(0, 0) to TileRecord.solid(epoch, block, height = 64, biome = 1)),
        )

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
    fun stepsWalkFromLiveThroughSnapshotsAndBack() = withTree { tree ->
        tree.commitAt(100, 1)
        tree.commitAt(200, 2)
        tree.commitAt(300, 3)
        val history = MapHistoryBrowser(tree)
        assertEquals(MapTime.Live, history.time)
        history.open()
        assertEquals(4, history.labels.size)
        history.step(1)
        assertEquals(MapTime.At(300), history.time)
        assertEquals("Live", history.labels[0])
        history.step(2)
        assertEquals(MapTime.At(100), history.time)
        history.step(5)
        assertEquals(MapTime.At(100), history.time)
        history.step(-9)
        assertEquals(MapTime.Live, history.time)
        history.select(2)
        assertEquals(MapTime.At(200), history.time)
        history.close()
        assertEquals(MapTime.Live, history.time)
        assertTrue(!history.open)
    }

    @Test
    fun newCommitsWhileBrowsingKeepTheSelectedSnapshot() = withTree { tree ->
        tree.commitAt(100, 1)
        tree.commitAt(200, 2)
        val history = MapHistoryBrowser(tree)
        history.open()
        history.step(2)
        assertEquals(MapTime.At(100), history.time)
        tree.commitAt(300, 3)
        history.advance(nanos)
        nanos += FRAME_NANOS
        history.advance(nanos)
        assertEquals(MapTime.At(100), history.time)
        assertEquals(4, history.labels.size)
    }

    @Test
    fun thePositionGlidesToTheSelectionAndADragSnapsToTheNearestRow() = withTree { tree ->
        for (i in 1..40) tree.commitAt(i * 100L, i)
        val history = MapHistoryBrowser(tree)
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
        assertEquals(MapTime.At(3100), history.time)

        history.adopt(20.4)
        assertEquals(20, history.entry)
        assertEquals(20.4, history.position)
        history.settle()
        assertEquals(20.0, history.position)
        assertEquals(MapTime.At(2100), history.time)
    }

    private inline fun withTree(test: (MapTree) -> Unit) =
        TestBlocks.withDirectory("palimpsest-history-") { directory ->
            MapTree(directory, machineId = 1).use(test)
        }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
    }
}
