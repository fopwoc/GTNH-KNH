package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.RootIndex
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The snapshot browser: every commit of the map as a strip, newest on top, with the live view above
 * them. Wheel notches step one snapshot at a time and [position] glides after the selection so the
 * strip can keep it centred; dragging the strip adopts whatever lands in the middle.
 */
class MapHistoryBrowser(private val tree: MapTree) {
    var open = false
        private set

    /** Row labels, live first then commits newest first; entry `i` is `epochs[size - i]`. */
    var labels: List<String> = listOf(LIVE_LABEL)
        private set

    var entry = 0
        private set

    /** Eased row position of the selection, in entries; what the strip actually shows. */
    var position = 0.0
        private set

    private var epochs = LongArray(0)
    private var roots: RootIndex? = null
    private var lastFrameNanos = 0L

    val time: MapTime
        get() = if (entry == 0) MapTime.Live else MapTime.At(epochs[epochs.size - entry])

    /** The tiles the selected snapshot changed against the one before it; none for live. */
    fun changedTiles(limit: Int): List<TileKey> {
        if (entry == 0) return emptyList()
        val index = epochs.size - entry
        val previous = if (index > 0) epochs[index - 1] else -1L
        return tree.changedTiles(previous, epochs[index], limit)
    }

    fun open() {
        open = true
        refresh()
    }

    /** Closes the strip and returns the map to the live view. */
    fun close() {
        open = false
        entry = 0
        position = 0.0
    }

    fun step(delta: Int) = select(entry + delta)

    fun select(index: Int) {
        entry = index.coerceIn(0, epochs.size)
    }

    /** The strip was dragged to [rowPosition] entries; it snaps to the nearest one from there. */
    fun adopt(rowPosition: Double) {
        position = rowPosition.coerceIn(0.0, epochs.size.toDouble())
        select(position.roundToInt())
    }

    /** Glides toward the selection and picks up new commits; true when the strip moved. */
    fun advance(frameNanos: Long): Boolean {
        val dt =
            if (lastFrameNanos == 0L) 0.0
            else ((frameNanos - lastFrameNanos) / NANOS_PER_SECOND).coerceIn(0.0, MAX_FRAME_SECONDS)
        lastFrameNanos = frameNanos
        if (!open || dt == 0.0) return false
        val refreshed = refresh()
        val remaining = entry - position
        if (remaining == 0.0) return refreshed
        position =
            if (abs(remaining) < SETTLE_ENTRIES) entry.toDouble()
            else position + remaining * (1 - exp(-dt / SCROLL_SECONDS))
        return true
    }

    private fun refresh(): Boolean {
        val current = tree.roots
        if (current === roots) return false
        roots = current
        val previous = epochs.size
        epochs = current.epochs()
        labels = buildList {
            add(LIVE_LABEL)
            for (index in epochs.indices.reversed()) add(formatEpoch(epochs[index]))
        }
        // Entries count from the newest, so commits landing while browsing must not shift the
        // selection onto a different snapshot.
        if (entry > 0) entry = (entry + epochs.size - previous).coerceIn(0, epochs.size)
        return true
    }

    companion object {
        const val LIVE_LABEL = "Live"
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val MAX_FRAME_SECONDS = 0.1
        private const val SCROLL_SECONDS = 0.12
        private const val SETTLE_ENTRIES = 0.002
    }
}
