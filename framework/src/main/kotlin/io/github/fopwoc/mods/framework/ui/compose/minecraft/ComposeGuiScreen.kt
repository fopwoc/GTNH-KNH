package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
abstract class ComposeGuiScreen : GuiScreen() {
    private val session = ComposeGuiScreenSession(screen = this) {
        Content()
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

    override fun initGui() {
        super.initGui()
        session.initialize()
    }

    override fun onGuiClosed() {
        session.dispose()
        super.onGuiClosed()
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
            drawBackground = ::drawComposeBackground,
            drawTooltip = { lines, x, y ->
                drawHoveringText(lines, x, y, fontRendererObj)
            },
            fillRectBlock = { left, top, right, bottom, color ->
                drawRect(left, top, right, bottom, color)
            },
            drawHorizontalLineBlock = this::drawHorizontalLine,
            drawVerticalLineBlock = this::drawVerticalLine,
            fallback = {
                super.drawScreen(mouseX, mouseY, partialTicks)
            }
        )
    }

}
