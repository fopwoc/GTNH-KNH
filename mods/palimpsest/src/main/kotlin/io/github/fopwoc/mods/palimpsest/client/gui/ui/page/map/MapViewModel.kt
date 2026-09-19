package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera and snapshot selection of the map screen. Pointer, wheel and key input arrive as calls
 * from the screen; [advance] runs once per render frame and moves whatever is still gliding.
 */
class MapViewModel(private val session: MapSession, centerX: Double, centerZ: Double) :
    ViewModel() {
    private val camera = MapCameraMotion(centerX, centerZ)
    private val history = MapHistoryBrowser(session.map.store.tree)
    private val mutableModel = MutableStateFlow(snapshot())

    val model: StateFlow<MapModel> = mutableModel.asStateFlow()

    val historyOpen: Boolean
        get() = history.open

    /** Draw commands for the current camera and moment; pages missing here are being built. */
    fun frame(width: Int, height: Int): GpuCanvasFrame =
        session.map.view.frame(camera.camera(width, height), history.time)

    fun advance(frameNanos: Long, width: Int, height: Int) {
        val moved = camera.advance(frameNanos, width, height)
        val glided = history.advance(frameNanos)
        if (moved || glided) publish()
    }

    fun dragBy(dx: Double, dy: Double, nowNanos: Long) = update { camera.dragBy(dx, dy, nowNanos) }

    fun endDrag(nowNanos: Long) = camera.endDrag(nowNanos)

    fun panBy(dx: Double, dy: Double) = camera.panBy(dx, dy)

    fun lookAt(x: Double, z: Double) = camera.lookAt(x, z)

    fun zoomBy(steps: Double, atX: Double, atY: Double) = camera.zoomBy(steps, atX, atY)

    fun openHistory() = update { history.open() }

    fun closeHistory() = update { history.close() }

    fun stepHistory(delta: Int) = update { history.step(delta) }

    fun selectSnapshot(entry: Int) = update { history.select(entry) }

    fun historyScrolledTo(rowPosition: Double) = update { history.adopt(rowPosition) }

    private inline fun update(change: () -> Unit) {
        change()
        publish()
    }

    private fun publish() {
        mutableModel.value = snapshot()
    }

    private fun snapshot() =
        MapModel(
            centerX = camera.centerX,
            centerZ = camera.centerZ,
            pixelsPerBlock = camera.pixelsPerBlock,
            time = history.time,
            history =
                if (history.open) MapHistoryModel(history.labels, history.entry, history.position)
                else null,
        )
}
