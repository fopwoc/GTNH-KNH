package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.lwjglKeyPress
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen

/** Shows a platform-neutral [ComposeScreen] as a 1.7.10 screen. */
@SideOnly(Side.CLIENT)
internal class GtnhComposeScreenHost(private val screen: ComposeScreen) : ComposeGuiScreen() {
    @Composable
    override fun Content() = screen.Content()

    override val composeBackgroundStyle: ComposeBackgroundStyle
        get() = screen.background

    override fun doesGuiPauseGame(): Boolean = screen.pausesGame

    override fun initGui() {
        screen.width = width
        screen.height = height
        super.initGui()
    }

    override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean = screen.onUnhandledKey(lwjglKeyPress(keyCode))

    override fun updateScreen() {
        super.updateScreen()
        screen.onTick()
        if (screen.closeRequested) {
            screen.closeRequested = false
            mc.displayGuiScreen(null)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        screen.onFrame()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun onScroll(x: Double, y: Double, notches: Double): Boolean = screen.onScroll(x, y, notches)

    override fun onGuiClosed() {
        super.onGuiClosed()
        screen.onClosed()
    }
}
