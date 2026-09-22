package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.GtnhRenderSurface
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftPrimitiveRenderCallbacks
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.ComposeGuiScreenSession
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.LwjglKeyboardEnvironment
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.LwjglMouseEventReader
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.isLwjglTypedChar
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.lwjglKeyPress
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.resolveMouseWheelEvent
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard

/** A 1.7.10 screen hosting a composed [Content]; input Compose does not consume reaches vanilla. */
@SideOnly(Side.CLIENT)
abstract class ComposeGuiScreen : GuiScreen() {
    private val primitives =
        object : MinecraftPrimitiveRenderCallbacks {
            override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) =
                drawRect(left, top, right, bottom, color)

            override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) =
                this@ComposeGuiScreen.drawHorizontalLine(startX, endX, y, color)

            override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) =
                this@ComposeGuiScreen.drawVerticalLine(x, startY, endY, color)
        }
    private val session = ComposeGuiScreenSession(GtnhRenderSurface(primitives)) { Content() }

    @Composable protected abstract fun Content()

    protected open val composeBackgroundStyle: ComposeBackgroundStyle
        get() = ComposeBackgroundStyle.Color(Color(0xA0101010))

    protected open fun drawComposeBackground() {
        when (val style = composeBackgroundStyle) {
            is ComposeBackgroundStyle.Color -> drawRect(0, 0, width, height, style.color.argbInt)
            ComposeBackgroundStyle.VanillaDefault -> drawDefaultBackground()
            ComposeBackgroundStyle.None -> Unit
        }
    }

    protected open fun drawComposeFallback(mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun initGui() {
        super.initGui()
        Keyboard.enableRepeatEvents(true)
        try {
            session.initialize()
        } catch (throwable: Throwable) {
            Keyboard.enableRepeatEvents(false)
            throw throwable
        }
    }

    override fun updateScreen() {
        super.updateScreen()
        session.tick(System.nanoTime())
    }

    override fun onGuiClosed() {
        try {
            session.dispose()
        } finally {
            Keyboard.enableRepeatEvents(false)
            super.onGuiClosed()
        }
    }

    /**
     * Keys that no text field, `BackHandler` or `NavHost` consumed, before vanilla handling (Escape
     * closes the screen). Return true to swallow the key.
     */
    protected open fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean = false

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // 1.7.10 delivers a key and its character together; Compose sees them as two inputs.
        val handledKey = session.keyPressed(lwjglKeyPress(keyCode), LwjglKeyboardEnvironment.clipboard)
        val handledChar = isLwjglTypedChar(typedChar) && session.charTyped(typedChar)
        if (!handledKey && !handledChar && !onUnhandledKey(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode)
        }
    }

    override fun handleMouseInput() {
        val wheel = LwjglMouseEventReader.readWheelEvent()
        super.handleMouseInput()
        val resolved = resolveMouseWheelEvent(width, height, mc?.displayWidth, mc?.displayHeight, wheel) ?: return
        session.mouseScrolled(resolved.mouseX, resolved.mouseY, resolved.wheelDelta)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (!session.mousePressed(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton)
        }
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        if (!session.mouseDragged(mouseX, mouseY, clickedMouseButton)) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
        }
    }

    override fun mouseMovedOrUp(mouseX: Int, mouseY: Int, state: Int) {
        if (state == -1) {
            session.mouseMoved()
            super.mouseMovedOrUp(mouseX, mouseY, state)
        } else if (!session.mouseReleased(mouseX, mouseY, state)) {
            super.mouseMovedOrUp(mouseX, mouseY, state)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawComposeBackground()
        if (mc == null || fontRendererObj == null) {
            drawComposeFallback(mouseX, mouseY, partialTicks)
            return
        }
        val tooltip = session.render(width, height, mouseX, mouseY)
        drawComposeFallback(mouseX, mouseY, partialTicks)
        tooltip?.let { drawHoveringText(it, mouseX, mouseY, fontRendererObj) }
    }
}
