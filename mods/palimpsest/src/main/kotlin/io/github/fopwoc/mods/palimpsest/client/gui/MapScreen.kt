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
    private var dragging = false

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

    override fun handleMouseInput() {
        super.handleMouseInput()
        val scaleX = width.toDouble() / mc.displayWidth
        val scaleY = height.toDouble() / mc.displayHeight
        val x = Mouse.getEventX() * scaleX
        val y = height - Mouse.getEventY() * scaleY
        val overCanvas = y < height - BAR_HEIGHT
        val now = System.nanoTime()
        when {
            Mouse.getEventButton() == 0 -> {
                if (Mouse.getEventButtonState()) {
                    dragging = overCanvas
                    if (dragging) state.dragBy(0.0, 0.0, now)
                } else if (dragging) {
                    dragging = false
                    state.endDrag(now)
                }
            }
            dragging -> state.dragBy(Mouse.getEventDX() * scaleX, -Mouse.getEventDY() * scaleY, now)
        }
        val wheel = Mouse.getEventDWheel()
        if (wheel != 0 && overCanvas) {
            state.zoomBy((wheel / WHEEL_NOTCH).coerceIn(-1.0, 1.0), x, y)
        }
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
        /** One mouse wheel notch; trackpads report fractions of it. */
        const val WHEEL_NOTCH = 120.0
    }
}
