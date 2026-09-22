package io.github.fopwoc.mods.framework.ui.compose.layout.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect

/**
 * Nested clip rectangles of one frame in GUI coordinates. Each change hands the effective
 * (intersected, viewport-bounded) rectangle to [apply], null for "no clip"; input targets are
 * clipped the same way so hit tests match what is visible.
 */
internal class ClipStack(
    viewportWidth: Int,
    viewportHeight: Int,
    private val appendInputTarget: (InputTarget) -> Unit,
    private val apply: (Rect?) -> Unit,
) {
    private val viewportBounds = Rect(0, 0, viewportWidth.coerceAtLeast(0), viewportHeight.coerceAtLeast(0))
    private var activeClipRect: Rect? = null

    fun registerInputTarget(target: InputTarget) {
        if (target.bounds.isEmpty()) return
        val combinedClipRect = normalize(mergeClipRects(activeClipRect, target.clipRect))
        if (combinedClipRect?.isEmpty() == true) return
        appendInputTarget(target.copy(clipRect = combinedClipRect))
    }

    fun withClipRect(rect: Rect, block: () -> Unit) {
        val previousClipRect = activeClipRect
        set(mergeClipRects(previousClipRect, rect))
        try {
            block()
        } finally {
            set(previousClipRect)
        }
    }

    fun reset() = set(null)

    private fun set(rect: Rect?) {
        val normalized = normalize(rect)
        activeClipRect = normalized
        apply(normalized)
    }

    private fun normalize(rect: Rect?): Rect? = rect?.intersect(viewportBounds)
}

internal fun mergeClipRects(first: Rect?, second: Rect?): Rect? {
    return when {
        first == null -> second
        second == null -> first
        else -> first.intersect(second)
    }
}
