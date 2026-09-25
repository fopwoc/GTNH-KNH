package io.github.fopwoc.mods.palimpsest.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.component.Scaffold
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeMenuScreen
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MAP_BAR_HEIGHT
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MAP_HISTORY_WIDTH
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapRoute
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapViewModel
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import kotlin.math.sign

/**
 * The world map. Drag to pan (with a fling on release), wheel or trackpad to zoom around the
 * cursor, arrows/WASD to pan, +/- to zoom, Home to jump back to the player; the history strip on
 * the right steps through snapshots with the wheel. Pointer input is read in display pixels so
 * panning stays smooth at any GUI scale.
 */
class MapScreen(toggleKey: KeyBinding? = null) : ComposeMenuScreen(toggleKey) {
    private val session: MapSession? = MapSessions.session
    /** Published from composition, where the store-owned ViewModel lives; input goes through it. */
    private var viewModel: MapViewModel? = null
    private var pointerDown = false
    private var dragging = false
    private var pointerX = 0.0
    private var pointerY = 0.0

    @Composable
    override fun Content() {
        val session = session
        if (session == null) {
            Scaffold(
                screenWidth = width,
                screenHeight = height,
                title = "Palimpsest",
                subtitle = "World map",
                onClose = ::close,
            ) {
                Text("No map open for this world yet")
            }
            return
        }
        val viewModel = viewModel {
            val player = ClientBackend.current.playerPosition
            MapViewModel(session, player?.x ?: 0.0, player?.z ?: 0.0)
        }
        SideEffect { this.viewModel = viewModel }
        MapRoute(viewModel, width, height, ::close)
    }

    /**
     * Mouse events can arrive at tick rate, so a drag driven by them stutters; the pointer is
     * sampled every frame instead and only the wheel still comes through events.
     */
    override fun onFrame() {
        val viewModel = viewModel ?: return
        val backend = ClientBackend.current
        val x = backend.pointerX
        val y = backend.pointerY
        val down = backend.isMouseButtonDown(0)
        val now = System.nanoTime()
        when {
            down && !pointerDown -> {
                dragging = overMap(x, y)
                if (dragging) viewModel.dragBy(0.0, 0.0, now)
            }
            down && dragging -> viewModel.dragBy(x - pointerX, y - pointerY, now)
            !down && dragging -> {
                dragging = false
                viewModel.endDrag(now)
            }
        }
        pointerDown = down
        pointerX = x
        pointerY = y
    }

    override fun onScroll(x: Double, y: Double, notches: Double): Boolean {
        val viewModel = viewModel ?: return false
        // Each event is one step; the eased camera turns a burst of them into a glide. The history
        // strip steps snapshots by notch instead of scrolling by pixels.
        return when {
            overHistory(x, y) -> {
                viewModel.stepHistory(-sign(notches).toInt())
                true
            }
            y < height - MAP_BAR_HEIGHT -> {
                viewModel.zoomBy(sign(notches), x, y)
                true
            }
            else -> false
        }
    }

    private fun overHistory(x: Double, y: Double): Boolean =
        viewModel?.historyOpen == true &&
            x >= width - MAP_HISTORY_WIDTH &&
            y < height - MAP_BAR_HEIGHT

    private fun overMap(x: Double, y: Double): Boolean =
        y < height - MAP_BAR_HEIGHT && !overHistory(x, y)

    override fun onUnhandledKey(press: KeyPress): Boolean {
        if (super.onUnhandledKey(press)) return true
        val viewModel = viewModel ?: return false
        val step = PAN_PIXELS.toDouble()
        val canvasHeight = height - MAP_BAR_HEIGHT
        when (press.key) {
            Key.Left,
            Key.A -> viewModel.panBy(step, 0.0)
            Key.Right,
            Key.D -> viewModel.panBy(-step, 0.0)
            Key.Up,
            Key.W -> viewModel.panBy(0.0, step)
            Key.Down,
            Key.S -> viewModel.panBy(0.0, -step)
            Key.Equals -> viewModel.zoomBy(1.0, width / 2.0, canvasHeight / 2.0)
            Key.Minus -> viewModel.zoomBy(-1.0, width / 2.0, canvasHeight / 2.0)
            Key.Home -> ClientBackend.current.playerPosition?.let { viewModel.lookAt(it.x, it.z) }
            else -> return false
        }
        return true
    }

    private companion object {
        const val PAN_PIXELS = 32
    }
}
