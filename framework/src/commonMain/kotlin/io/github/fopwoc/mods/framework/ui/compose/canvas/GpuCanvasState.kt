package io.github.fopwoc.mods.framework.ui.compose.canvas

import java.util.concurrent.atomic.AtomicReference

/** Latest canvas frame. Submitting a frame does not trigger Compose recomposition or layout. */
class GpuCanvasState(initialFrame: GpuCanvasFrame) {
    private val latest = AtomicReference(initialFrame)

    internal val frame: GpuCanvasFrame
        get() = latest.get()

    fun submit(frame: GpuCanvasFrame) {
        latest.set(frame)
    }
}
