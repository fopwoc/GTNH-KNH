package io.github.fopwoc.mods.framework.ui.compose.layout.render

import java.util.IdentityHashMap

/**
 * One platform renderer per GPU canvas node, kept while the node draws every frame and released
 * after the first frame it does not.
 */
internal class GpuCanvasCache<R>(private val create: () -> R, private val release: (R) -> Unit) {
    private class Entry<R>(val renderer: R, var lastFrame: Int)

    private val entries = IdentityHashMap<Any, Entry<R>>()
    private var frame = 0

    fun beginFrame() {
        frame++
    }

    fun renderer(handle: Any): R {
        val entry = entries.getOrPut(handle) { Entry(create(), frame) }
        entry.lastFrame = frame
        return entry.renderer
    }

    fun endFrame() {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.lastFrame == frame) continue
            release(entry.renderer)
            iterator.remove()
        }
    }

    fun dispose() {
        entries.values.forEach { release(it.renderer) }
        entries.clear()
    }
}
