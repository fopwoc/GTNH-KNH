package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.RootIndex
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The snapshot browser: every commit of the map as a strip, newest on top, with the live view above
 * them. Wheel notches step one snapshot at a time and the strip glides to keep the selected one
 * centred; dragging the strip's thumb picks whatever lands in the middle.
 */
class MapHistoryState(private val tree: MapTree) {
    var open by mutableStateOf(false)
        private set

    /** Commit epochs, oldest first; [entry] 0 is live and entry `i` is `epochs[size - i]`. */
    var epochs: LongArray by mutableStateOf(LongArray(0))
        private set

    var entry by mutableIntStateOf(0)
        private set

    /** Eased row position of the selection, in entries; what the strip actually shows. */
    var position by mutableDoubleStateOf(0.0)
        private set

    val list = LazyListState()
    private var roots: RootIndex? = null
    private var observedScroll = -1
    private var lastFrameNanos = 0L

    val entryCount: Int
        get() = epochs.size + 1

    val time: MapTime
        get() = if (entry == 0) MapTime.Live else MapTime.At(epochs[epochs.size - entry])

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

    fun label(index: Int): String =
        if (index == 0) "Live" else formatEpoch(epochs[epochs.size - index])

    /** Eases the strip toward the selection; [viewportHeight] is the strip's visible height. */
    fun advance(frameNanos: Long, viewportHeight: Int) {
        val dt =
            if (lastFrameNanos == 0L) 0.0
            else ((frameNanos - lastFrameNanos) / NANOS_PER_SECOND).coerceIn(0.0, MAX_FRAME_SECONDS)
        lastFrameNanos = frameNanos
        if (!open || dt == 0.0) return
        refresh()

        val centre = (viewportHeight - ROW_HEIGHT) / 2.0
        val scroll = list.scroll.value
        if (observedScroll >= 0 && scroll != observedScroll) {
            // The thumb was dragged: adopt where it went and snap to the nearest entry.
            position = (scroll + centre) / ROW_HEIGHT - leadingSpacers(viewportHeight)
            select(position.roundToInt())
        }

        val remaining = entry - position
        position =
            if (abs(remaining) < SETTLE_ENTRIES) entry.toDouble()
            else position + remaining * (1 - exp(-dt / SCROLL_SECONDS))
        val target =
            ((position + leadingSpacers(viewportHeight)) * ROW_HEIGHT - centre)
                .roundToInt()
                .coerceAtLeast(0)
        // Layout clamps the request, so remember what the state settled on, not what was asked.
        list.scroll.scrollTo(target)
        observedScroll = list.scroll.value
    }

    /** Empty rows above and below the entries so the first and last can still sit centred. */
    fun leadingSpacers(viewportHeight: Int): Int = (viewportHeight / 2) / ROW_HEIGHT

    private fun refresh() {
        val current = tree.roots
        if (current === roots) return
        roots = current
        val previous = epochs.size
        epochs = current.epochs()
        // Entries count from the newest, so commits landing while browsing must not shift the
        // selection onto a different snapshot.
        if (entry > 0) entry = (entry + epochs.size - previous).coerceIn(0, epochs.size)
    }

    companion object {
        const val ROW_HEIGHT = 14
        const val PANEL_WIDTH = 112
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val MAX_FRAME_SECONDS = 0.1
        private const val SCROLL_SECONDS = 0.12
        private const val SETTLE_ENTRIES = 0.002
    }
}
