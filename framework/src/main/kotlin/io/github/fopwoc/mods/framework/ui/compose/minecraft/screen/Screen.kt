package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.ComposeGuiScreenRenderCallbacks
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.ComposeGuiScreenSession
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
abstract class ComposeGuiScreen : GuiScreen() {
    private val session = ComposeGuiScreenSession(screen = this) {
        Content()
    }
    private val renderCallbacks = object : ComposeGuiScreenRenderCallbacks {
        override fun drawBackground() {
            drawComposeBackground()
        }

        override fun drawTooltip(lines: List<String>, x: Int, y: Int) {
            drawHoveringText(lines, x, y, fontRendererObj)
        }

        override fun drawFallback(mouseX: Int, mouseY: Int, partialTicks: Float) {
            drawComposeFallback(mouseX, mouseY, partialTicks)
        }

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            drawRect(left, top, right, bottom, color)
        }

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) {
            this@ComposeGuiScreen.drawHorizontalLine(startX, endX, y, color)
        }

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) {
            this@ComposeGuiScreen.drawVerticalLine(x, startY, endY, color)
        }
    }

    @Composable
    protected abstract fun Content()

    protected open val composeBackgroundStyle: ComposeBackgroundStyle
        get() = ComposeBackgroundStyle.Color(Color(0xA0101010))

    protected open fun drawComposeBackground() {
        when (val style = composeBackgroundStyle) {
            is ComposeBackgroundStyle.Color -> {
                drawRect(0, 0, width, height, style.color.argbInt)
            }
            ComposeBackgroundStyle.VanillaDefault -> {
                drawDefaultBackground()
            }
            ComposeBackgroundStyle.None -> Unit
        }
    }

    protected open fun drawComposeFallback(mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    protected open fun currentComposeFrameTimeNanos(): Long = System.nanoTime()

    protected open fun setComposeKeyboardRepeatEvents(enabled: Boolean) {
        Keyboard.enableRepeatEvents(enabled)
    }

    override fun initGui() {
        super.initGui()
        setComposeKeyboardRepeatEvents(true)
        try {
            session.initialize()
        } catch (throwable: Throwable) {
            setComposeKeyboardRepeatEvents(false)
            throw throwable
        }
    }

    override fun updateScreen() {
        super.updateScreen()
        session.updateScreen(currentComposeFrameTimeNanos())
    }

    override fun onGuiClosed() {
        try {
            session.dispose()
        } finally {
            setComposeKeyboardRepeatEvents(false)
            super.onGuiClosed()
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        session.keyTyped(typedChar, keyCode) {
            super.keyTyped(typedChar, keyCode)
        }
    }

    override fun handleMouseInput() {
        session.handleMouseInput(width, height, mc) {
            super.handleMouseInput()
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        session.mouseClicked(mouseX, mouseY, mouseButton) {
            super.mouseClicked(mouseX, mouseY, mouseButton)
        }
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        session.mouseClickMove(mouseX, mouseY, clickedMouseButton) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
        }
    }

    override fun mouseMovedOrUp(mouseX: Int, mouseY: Int, state: Int) {
        session.mouseMovedOrUp(mouseX, mouseY, state) {
            super.mouseMovedOrUp(mouseX, mouseY, state)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        session.drawScreen(
            client = mc,
            font = fontRendererObj,
            width = width,
            height = height,
            mouseX = mouseX,
            mouseY = mouseY,
            partialTicks = partialTicks,
            callbacks = renderCallbacks
        )
    }

}
