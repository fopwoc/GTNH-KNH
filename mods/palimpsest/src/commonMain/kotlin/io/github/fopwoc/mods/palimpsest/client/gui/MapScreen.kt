package io.github.fopwoc.mods.palimpsest.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.component.Scaffold
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeMenuScreen
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MAP_BAR_HEIGHT
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MAP_EDITOR_WIDTH
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MAP_HISTORY_WIDTH
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapRoute
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapViewModel
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.ViewControlsLayout
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso.ViewCube
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.mapPanelWidth
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import kotlin.math.abs
import kotlin.math.sign

/**
 * The world map. Drag to pan (with a fling on release), wheel or trackpad to zoom around the
 * cursor, arrows/WASD to pan, +/- to zoom, Home to jump back to the player; the history strip on
 * the right steps through snapshots with the wheel. With history the map turns 3D: the view cube
 * snaps to what is clicked and orbits when dragged, as does the middle button on the map, Q and E
 * turn by quarters, and the rail under the cube cuts the world away above a height. Pointer input
 * is read in display pixels so panning stays smooth at any GUI scale.
 */
class MapScreen(toggleKey: KeyBinding? = null) : ComposeMenuScreen(toggleKey) {
    private val session: MapSession? = MapSessions.session
    /** Published from composition, where the store-owned ViewModel lives; input goes through it. */
    private var viewModel: MapViewModel? = null
    private var pointerDown = false
    private var rightPointerDown = false
    private var dragging = false
    private var pointerX = 0.0
    private var pointerY = 0.0
    /** A press on the view cube: a click if it stays put, an orbit once it moves. */
    private var cubePress: Pair<Double, Double>? = null
    private var cubeTravel = 0.0
    private var railDragging = false
    private var middlePointerDown = false
    private var orbiting = false

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
        val rightDown = backend.isMouseButtonDown(1)
        val middleDown = backend.isMouseButtonDown(2)
        val now = System.nanoTime()
        if (!controlsPointer(viewModel, x, y, down)) mapPointer(viewModel, x, y, down, now)
        pointerDown = down
        middleOrbit(viewModel, x, y, middleDown)
        if (rightDown && !rightPointerDown && overMap(x, y)) {
            viewModel.openWaypointAt(x, y, width, height - MAP_BAR_HEIGHT)
        }
        rightPointerDown = rightDown
        pointerX = x
        pointerY = y
    }

    /** The view cube and the slice rail; true while they own the left button. */
    private fun controlsPointer(
        viewModel: MapViewModel,
        x: Double,
        y: Double,
        down: Boolean,
    ): Boolean {
        viewModel.hoverCube(
            (x - controlsLeft()).takeIf { overCube(x, y) },
            (y - ViewControlsLayout.TOP).takeIf { overCube(x, y) },
        )
        val press = cubePress
        when {
            down && !pointerDown && overCube(x, y) -> {
                cubePress = x to y
                cubeTravel = 0.0
            }
            down && !pointerDown && overRail(x, y) -> railDragging = true
            down && press != null -> {
                cubeTravel += abs(x - pointerX) + abs(y - pointerY)
                if (cubeTravel > CLICK_SLOP) viewModel.orbitBy(x - pointerX, y - pointerY)
            }
            !down && press != null -> {
                cubePress = null
                if (cubeTravel > CLICK_SLOP) viewModel.endOrbit()
                else
                    viewModel.clickCube(
                        press.first - controlsLeft(),
                        press.second - ViewControlsLayout.TOP,
                    )
            }
            down && railDragging -> slideRail(y)
            !down && railDragging -> railDragging = false
            else -> return false
        }
        return true
    }

    private fun mapPointer(
        viewModel: MapViewModel,
        x: Double,
        y: Double,
        down: Boolean,
        now: Long,
    ) {
        when {
            down && !pointerDown -> {
                dragging =
                    overMap(x, y) && !viewModel.editWaypointAt(x, y, width, height - MAP_BAR_HEIGHT)
                if (dragging) viewModel.dragBy(0.0, 0.0, now)
            }
            down && dragging -> viewModel.dragBy(x - pointerX, y - pointerY, now)
            !down && dragging -> {
                dragging = false
                viewModel.endDrag(now)
            }
        }
    }

    /** The middle button orbits anywhere on the map, as in a CAD viewport. */
    private fun middleOrbit(viewModel: MapViewModel, x: Double, y: Double, down: Boolean) {
        when {
            down && !middlePointerDown -> orbiting = overMap(x, y)
            down && orbiting -> viewModel.orbitBy(x - pointerX, y - pointerY)
            !down && orbiting -> {
                orbiting = false
                viewModel.endOrbit()
            }
        }
        middlePointerDown = down
    }

    override fun onScroll(x: Double, y: Double, notches: Double): Boolean {
        val viewModel = viewModel ?: return false
        // Each event is one step; the eased camera turns a burst of them into a glide. The history
        // strip steps snapshots by notch instead of scrolling by pixels.
        return when {
            overRail(x, y) -> {
                viewModel.stepSlice(sign(notches).toInt())
                true
            }
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

    private fun slideRail(y: Double) {
        val viewModel = viewModel ?: return
        viewModel.sliceAt(
            ViewControlsLayout.sliceAt(
                y - ViewControlsLayout.TOP - ViewControlsLayout.RAIL_TOP,
                viewModel.sliceRange,
            )
        )
    }

    private fun controlsLeft(): Int =
        ViewControlsLayout.left(
            width,
            mapPanelWidth(viewModel?.historyOpen == true, viewModel?.waypointPanelOpen == true),
        )

    private fun overControls(x: Double, y: Double): Boolean =
        viewModel?.viewAvailable == true &&
            x >= controlsLeft() &&
            x < controlsLeft() + ViewControlsLayout.WIDTH &&
            y >= ViewControlsLayout.TOP &&
            y < ViewControlsLayout.TOP + ViewControlsLayout.HEIGHT

    private fun overCube(x: Double, y: Double): Boolean =
        overControls(x, y) && y < ViewControlsLayout.TOP + ViewCube.SIZE

    private fun overRail(x: Double, y: Double): Boolean =
        overControls(x, y) && y >= ViewControlsLayout.TOP + ViewControlsLayout.RAIL_TOP - RAIL_GRIP

    private fun overMap(x: Double, y: Double): Boolean =
        !overControls(x, y) &&
            x >= 0 &&
            x < width &&
            y >= 0 &&
            y < height - MAP_BAR_HEIGHT &&
            !overHistory(x, y) &&
            !(viewModel?.waypointPanelOpen == true && x >= width - MAP_EDITOR_WIDTH)

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
            Key.Q -> viewModel.turnQuarters(-1)
            Key.E -> viewModel.turnQuarters(1)
            Key.Home -> ClientBackend.current.playerPosition?.let { viewModel.lookAt(it.x, it.z) }
            else -> return false
        }
        return true
    }

    private companion object {
        const val PAN_PIXELS = 32
        /** Pointer travel, in pixels, past which a press on the cube is a drag, not a click. */
        const val CLICK_SLOP = 3.0
        /** The rail takes presses a little above its top, where "everything" is. */
        const val RAIL_GRIP = 4
    }
}
