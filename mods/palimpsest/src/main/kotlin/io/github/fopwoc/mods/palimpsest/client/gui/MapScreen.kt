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
 * The world map. Drag to pan, wheel to zoom around the cursor, arrows/WASD to pan, +/- to zoom,
 * Home to jump back to the player; the bar below the map scrubs time.
 */
@SideOnly(Side.CLIENT)
class MapScreen(toggleKey: KeyBinding? = null) : ComposeMenuScreen(toggleKey) {
    private val state: MapViewState = run {
        val player = Minecraft.getMinecraft().thePlayer
        MapViewState(player?.posX ?: 0.0, player?.posZ ?: 0.0)
    }
    private var lastMouseX = 0
    private var lastMouseY = 0

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
        MapRoute(session, state, width, height, refreshToken, ::requestClose)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, button: Int, timeSinceLastClick: Long) {
        super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick)
        if (button == 0 && mouseY < height - BAR_HEIGHT) {
            state.panPixels((mouseX - lastMouseX).toDouble(), (mouseY - lastMouseY).toDouble())
        }
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel == 0) return
        val x = Mouse.getEventX() * width / mc.displayWidth
        val y = height - Mouse.getEventY() * height / mc.displayHeight - 1
        if (y >= height - BAR_HEIGHT) return
        state.zoom(if (wheel > 0) 1 else -1, x.toDouble(), y.toDouble(), width, height - BAR_HEIGHT)
    }

    override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
        if (super.onUnhandledKey(typedChar, keyCode)) return true
        val step = PAN_PIXELS.toDouble()
        when (keyCode) {
            Keyboard.KEY_LEFT,
            Keyboard.KEY_A -> state.panPixels(step, 0.0)
            Keyboard.KEY_RIGHT,
            Keyboard.KEY_D -> state.panPixels(-step, 0.0)
            Keyboard.KEY_UP,
            Keyboard.KEY_W -> state.panPixels(0.0, step)
            Keyboard.KEY_DOWN,
            Keyboard.KEY_S -> state.panPixels(0.0, -step)
            Keyboard.KEY_EQUALS,
            Keyboard.KEY_ADD -> state.zoom(1, width / 2.0, height / 2.0, width, height)
            Keyboard.KEY_MINUS,
            Keyboard.KEY_SUBTRACT -> state.zoom(-1, width / 2.0, height / 2.0, width, height)
            Keyboard.KEY_HOME ->
                mc.thePlayer?.let {
                    state.centerX = it.posX
                    state.centerZ = it.posZ
                }
            else -> return false
        }
        refreshNow()
        return true
    }

    private companion object {
        const val BAR_HEIGHT = 22
        const val PAN_PIXELS = 32
    }
}
