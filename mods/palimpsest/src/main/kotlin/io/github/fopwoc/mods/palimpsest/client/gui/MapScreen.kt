package io.github.fopwoc.mods.palimpsest.client.gui

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.component.Scaffold
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeMenuScreen
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapRoute
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapViewState
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import kotlin.math.sign
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

/**
 * The world map. Drag to pan (with a fling on release), wheel or trackpad to zoom around the
 * cursor, arrows/WASD to pan, +/- to zoom, Home to jump back to the player; the bar below the map
 * scrubs time. Pointer input is read in display pixels so panning stays smooth at any GUI scale.
 */
@SideOnly(Side.CLIENT)
class MapScreen(toggleKey: KeyBinding? = null) : ComposeMenuScreen(toggleKey) {
    private val state: MapViewState = run {
        val player = Minecraft.getMinecraft().thePlayer
        MapViewState(player?.posX ?: 0.0, player?.posZ ?: 0.0)
    }
    private var pointerDown = false
    private var dragging = false
    private var pointerX = 0.0
    private var pointerY = 0.0

    @Composable
    override fun Content() {
        val session = MapSessions.session
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
        MapRoute(session, state, width, height, ::requestClose)
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
        val x = Mouse.getX() * width.toDouble() / mc.displayWidth
        val y = height - Mouse.getY() * height.toDouble() / mc.displayHeight
        val down = Mouse.isButtonDown(0)
        val now = System.nanoTime()
        when {
            down && !pointerDown -> {
                dragging = y < height - BAR_HEIGHT
                if (dragging) state.dragBy(0.0, 0.0, now)
            }
            down && dragging -> state.dragBy(x - pointerX, y - pointerY, now)
            !down && dragging -> {
                dragging = false
                state.endDrag(now)
            }
        }
        pointerDown = down
        pointerX = x
        pointerY = y
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel == 0) return
        val x = Mouse.getEventX() * width.toDouble() / mc.displayWidth
        val y = height - Mouse.getEventY() * height.toDouble() / mc.displayHeight
        // lwjgl3ify reports one unit per notch and folds trackpad fractions into whole notches, so
        // each event is one step; the eased camera turns a burst of them into a glide.
        if (y < height - BAR_HEIGHT) state.zoomBy(sign(wheel.toDouble()), x, y)
    }

    override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
        if (super.onUnhandledKey(typedChar, keyCode)) return true
        val step = PAN_PIXELS.toDouble()
        val canvasHeight = height - BAR_HEIGHT
        when (keyCode) {
            Keyboard.KEY_LEFT,
            Keyboard.KEY_A -> state.panBy(step, 0.0)
            Keyboard.KEY_RIGHT,
            Keyboard.KEY_D -> state.panBy(-step, 0.0)
            Keyboard.KEY_UP,
            Keyboard.KEY_W -> state.panBy(0.0, step)
            Keyboard.KEY_DOWN,
            Keyboard.KEY_S -> state.panBy(0.0, -step)
            Keyboard.KEY_EQUALS,
            Keyboard.KEY_ADD -> state.zoomBy(1.0, width / 2.0, canvasHeight / 2.0)
            Keyboard.KEY_MINUS,
            Keyboard.KEY_SUBTRACT -> state.zoomBy(-1.0, width / 2.0, canvasHeight / 2.0)
            Keyboard.KEY_HOME -> mc.thePlayer?.let { state.lookAt(it.posX, it.posZ) }
            else -> return false
        }
        return true
    }

    private companion object {
        const val BAR_HEIGHT = 22
        const val PAN_PIXELS = 32
    }
}
