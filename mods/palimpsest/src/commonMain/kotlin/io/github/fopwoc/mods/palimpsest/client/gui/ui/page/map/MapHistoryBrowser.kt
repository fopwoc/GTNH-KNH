package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.client.motion.easeStep
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.palimpsest.db.CommitTimeline
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.WorldTick
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The snapshot browser: every commit of the map as a strip, newest on top, with the live view above
 * them. Wheel notches step one snapshot at a time and [position] glides after the selection so the
 * strip can keep it centred; dragging the strip adopts whatever lands in the middle.
 */
class MapHistoryBrowser(private val history: Dimension?) {
    var open = false
        private set

    /**
     * Row labels, live first then commits newest first; entry `i` is the commit `size - i` from the
     * oldest.
     */
    var labels: List<String> = listOf(LIVE_LABEL)
        private set

    var entry = 0
        private set

    /** Eased row position of the selection, in entries; what the strip actually shows. */
    var position = 0.0
        private set

    private var commits: CommitTimeline? = null
    private var ticks = LongArray(0)
    private var observed = LongArray(0)
    private val clock = FrameClock(MAX_FRAME_SECONDS)

    val time: MapTime
        get() =
            if (entry == 0) MapTime.Live
            else MapTime.At(ticks[ticks.size - entry], observed[ticks.size - entry])

    /**
     * The chunks the selected moment changed on the surface against the one before it; none for
     * live. Asks history and waits, so call it off the render path or rarely.
     */
    fun changedTiles(limit: Int): List<TileKey> {
        val history = history ?: return emptyList()
        if (entry == 0) return emptyList()
        val index = ticks.size - entry
        val previous = if (index > 0) ticks[index - 1] else -1L
        return history
            .diff(WorldTick(previous), WorldTick(ticks[index]))
            .result
            .get()
            .changes
            .asSequence()
            .filter { it.surface }
            .take(limit)
            .map { TileKey(it.pos.x, it.pos.z) }
            .toList()
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
        entry = index.coerceIn(0, ticks.size)
    }

    /** The strip was dragged to [rowPosition] entries; it snaps to the nearest one from there. */
    fun adopt(rowPosition: Double) {
        position = rowPosition.coerceIn(0.0, ticks.size.toDouble())
        select(position.roundToInt())
    }

    /** Glides toward the selection and picks up new commits; true when the strip moved. */
    fun advance(frameNanos: Long): Boolean {
        val dt = clock.tick(frameNanos)
        if (!open || dt == 0.0) return false
        val refreshed = refresh()
        val remaining = entry - position
        if (remaining == 0.0) return refreshed
        position =
            if (abs(remaining) < SETTLE_ENTRIES) entry.toDouble()
            else position + remaining * easeStep(dt, SCROLL_SECONDS)
        return true
    }

    private fun refresh(): Boolean {
        val current = history?.timeline() ?: return false
        if (current === commits) return false
        commits = current
        val previous = ticks.size
        ticks = LongArray(current.size) { current[it].tick.value }
        observed = LongArray(current.size) { current[it].observedAt }
        labels = buildList {
            add(LIVE_LABEL)
            for (index in observed.indices.reversed()) add(formatEpoch(observed[index]))
        }
        // Entries count from the newest, so commits landing while browsing must not shift the
        // selection onto a different snapshot.
        if (entry > 0) entry = (entry + ticks.size - previous).coerceIn(0, ticks.size)
        return true
    }

    companion object {
        const val LIVE_LABEL = "Live"
        private const val MAX_FRAME_SECONDS = 0.1
        private const val SCROLL_SECONDS = 0.12
        private const val SETTLE_ENTRIES = 0.002
    }
}
