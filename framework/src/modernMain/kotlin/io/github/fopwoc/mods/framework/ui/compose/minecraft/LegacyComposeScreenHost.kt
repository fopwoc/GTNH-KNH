/*? if <26 {*/
/*package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.ModernRenderSurface
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.ComposeGuiScreenSession
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.glfwKeyPress
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

// Shows a platform-neutral ComposeScreen as a Minecraft 1.21.1 screen; ModernComposeScreenHost is
// the 26.x one.
internal class LegacyComposeScreenHost(private val screen: ComposeScreen) :
    Screen(Component.empty()) {
    private val surface = ModernRenderSurface()
    private val session = ComposeGuiScreenSession(surface) { screen.Content() }
    private val clipboard =
        object : TextClipboard {
            override fun read(): String = Minecraft.getInstance().keyboardHandler.clipboard

            override fun write(text: String) {
                Minecraft.getInstance().keyboardHandler.clipboard = text
            }
        }

    override fun init() {
        screen.width = width
        screen.height = height
        session.initialize()
    }

    override fun tick() {
        session.tick(System.nanoTime())
        screen.onTick()
        if (screen.closeRequested) {
            screen.closeRequested = false
            onClose()
        }
    }

    override fun removed() {
        session.dispose()
        screen.onClosed()
    }

    override fun isPauseScreen(): Boolean = screen.pausesGame

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, a: Float) {
        when (val style = screen.background) {
            is ComposeBackgroundStyle.Color ->
                graphics.fill(0, 0, width, height, style.color.argbInt)
            ComposeBackgroundStyle.VanillaDefault ->
                super.renderBackground(graphics, mouseX, mouseY, a)
            ComposeBackgroundStyle.None -> Unit
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, a: Float) {
        super.render(graphics, mouseX, mouseY, a)
        screen.onFrame()
        val tooltip = surface.drawInto(graphics) { session.render(width, height, mouseX, mouseY) }
        tooltip?.let { lines ->
            setTooltipForNextRenderPass(lines.map { Component.literal(it).visualOrderText })
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val press = glfwKeyPress(keyCode)
        return session.keyPressed(press, clipboard) ||
            screen.onUnhandledKey(press) ||
            super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        session.charTyped(codePoint) || super.charTyped(codePoint, modifiers)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean =
        session.mousePressed(mouseX.toInt(), mouseY.toInt(), button) ||
            super.mouseClicked(mouseX, mouseY, button)

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean =
        session.mouseReleased(mouseX.toInt(), mouseY.toInt(), button) ||
            super.mouseReleased(mouseX, mouseY, button)

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double,
    ): Boolean =
        session.mouseDragged(mouseX.toInt(), mouseY.toInt(), button) ||
            super.mouseDragged(mouseX, mouseY, button, dragX, dragY)

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        session.mouseMoved()
        super.mouseMoved(mouseX, mouseY)
    }

    // Compose's wheel handling uses LWJGL 2 units: 120 per notch.
    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean =
        screen.onScroll(x, y, scrollY) ||
            session.mouseScrolled(x.toInt(), y.toInt(), (scrollY * WHEEL_NOTCH).roundToInt()) ||
            super.mouseScrolled(x, y, scrollX, scrollY)

    private companion object {
        const val WHEEL_NOTCH = 120
    }
}
*//*?}*/
