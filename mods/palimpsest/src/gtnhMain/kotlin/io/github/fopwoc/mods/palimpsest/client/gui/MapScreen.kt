package io.github.fopwoc.mods.palimpsest.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.component.Scaffold
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeMenuScreen
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MAP_BAR_HEIGHT
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MAP_HISTORY_WIDTH
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapRoute
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapViewModel
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import kotlin.math.sign
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

/**
 * The world map. Drag to pan (with a fling on release), wheel or trackpad to zoom around the
 * cursor, arrows/WASD to pan, +/- to zoom, Home to jump back to the player; the history strip on
 * the right steps through snapshots with the wheel. Pointer input is read in display pixels so
 * panning stays smooth at any GUI scale.
 */
@SideOnly(Side.CLIENT)
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
                onClose = ::requestClose,
            ) {
                Text("No map open for this world yet")
            }
            return
        }
        val viewModel = viewModel {
            val player = Minecraft.getMinecraft().thePlayer
            MapViewModel(session, player?.posX ?: 0.0, player?.posZ ?: 0.0)
        }
        SideEffect { this.viewModel = viewModel }
        MapRoute(viewModel, width, height, ::requestClose)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        pollPointer()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    /**
     * Vanilla delivers mouse events from the 20 Hz tick, so a drag driven by them stutters; the
     * pointer is sampled every frame instead and only the wheel still comes through events.
     */
    private fun pollPointer() {
        val viewModel = viewModel ?: return
        val x = Mouse.getX() * width.toDouble() / mc.displayWidth
        val y = height - Mouse.getY() * height.toDouble() / mc.displayHeight
        val down = Mouse.isButtonDown(0)
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

    override fun handleMouseInput() {
        val wheel = Mouse.getEventDWheel()
        val x = Mouse.getEventX() * width.toDouble() / mc.displayWidth
        val y = height - Mouse.getEventY() * height.toDouble() / mc.displayHeight
        // The strip's own wheel handling would scroll it by pixels; notches step snapshots instead.
        if (wheel == 0 || !overHistory(x, y)) super.handleMouseInput()
        if (wheel == 0) return
        val viewModel = viewModel ?: return
        // lwjgl3ify reports one unit per notch and folds trackpad fractions into whole notches, so
        // each event is one step; the eased camera turns a burst of them into a glide.
        when {
            overHistory(x, y) -> viewModel.stepHistory(-sign(wheel.toDouble()).toInt())
            y < height - MAP_BAR_HEIGHT -> viewModel.zoomBy(sign(wheel.toDouble()), x, y)
        }
    }

    private fun overHistory(x: Double, y: Double): Boolean =
        viewModel?.historyOpen == true &&
            x >= width - MAP_HISTORY_WIDTH &&
            y < height - MAP_BAR_HEIGHT

    private fun overMap(x: Double, y: Double): Boolean =
        y < height - MAP_BAR_HEIGHT && !overHistory(x, y)

    override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
        if (super.onUnhandledKey(typedChar, keyCode)) return true
        val viewModel = viewModel ?: return false
        val step = PAN_PIXELS.toDouble()
        val canvasHeight = height - MAP_BAR_HEIGHT
        when (keyCode) {
            Keyboard.KEY_LEFT,
            Keyboard.KEY_A -> viewModel.panBy(step, 0.0)
            Keyboard.KEY_RIGHT,
            Keyboard.KEY_D -> viewModel.panBy(-step, 0.0)
            Keyboard.KEY_UP,
            Keyboard.KEY_W -> viewModel.panBy(0.0, step)
            Keyboard.KEY_DOWN,
            Keyboard.KEY_S -> viewModel.panBy(0.0, -step)
            Keyboard.KEY_EQUALS,
            Keyboard.KEY_ADD -> viewModel.zoomBy(1.0, width / 2.0, canvasHeight / 2.0)
            Keyboard.KEY_MINUS,
            Keyboard.KEY_SUBTRACT -> viewModel.zoomBy(-1.0, width / 2.0, canvasHeight / 2.0)
            Keyboard.KEY_HOME -> mc.thePlayer?.let { viewModel.lookAt(it.posX, it.posZ) }
            else -> return false
        }
        return true
    }

    private companion object {
        const val PAN_PIXELS = 32
    }
}
