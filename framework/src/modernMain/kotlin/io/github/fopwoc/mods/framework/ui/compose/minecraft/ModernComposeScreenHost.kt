/*? if >=26 {*/
// 26.x only: event-object input and render-state extraction; LegacyComposeScreenHost is 1.21.1.
package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.ModernRenderSurface
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.ComposeGuiScreenSession
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.toKeyPress
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** Shows a platform-neutral [ComposeScreen] as a Minecraft 26.x screen. */
internal class ModernComposeScreenHost(private val screen: ComposeScreen) :
    Screen(Component.empty()) {
    private val surface = ModernRenderSurface()
    private val session = ComposeGuiScreenSession(surface) { screen.Content() }
    private val clipboard =
        object : TextClipboard {
            override fun read(): String = minecraft.keyboardHandler.clipboard

            override fun write(text: String) {
                minecraft.keyboardHandler.clipboard = text
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

    override fun extractBackground(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        a: Float,
    ) {
        when (val style = screen.background) {
            is ComposeBackgroundStyle.Color ->
                graphics.fill(0, 0, width, height, style.color.argbInt)
            ComposeBackgroundStyle.VanillaDefault ->
                super.extractBackground(graphics, mouseX, mouseY, a)
            ComposeBackgroundStyle.None -> Unit
        }
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        a: Float,
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        screen.onFrame()
        val tooltip = surface.drawInto(graphics) { session.render(width, height, mouseX, mouseY) }
        tooltip?.let { lines ->
            graphics.setTooltipForNextFrame(
                font,
                lines.map { Component.literal(it).visualOrderText },
                mouseX,
                mouseY,
            )
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val press = event.toKeyPress()
        return session.keyPressed(press, clipboard) ||
            screen.onUnhandledKey(press) ||
            super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val handled =
            Character.toChars(event.codepoint()).fold(false) { consumed, char ->
                session.charTyped(char) || consumed
            }
        return handled || super.charTyped(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
        session.mousePressed(event.x().toInt(), event.y().toInt(), event.button()) ||
            super.mouseClicked(event, doubleClick)

    override fun mouseReleased(event: MouseButtonEvent): Boolean =
        session.mouseReleased(event.x().toInt(), event.y().toInt(), event.button()) ||
            super.mouseReleased(event)

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean =
        session.mouseDragged(event.x().toInt(), event.y().toInt(), event.button()) ||
            super.mouseDragged(event, dx, dy)

    override fun mouseMoved(x: Double, y: Double) {
        session.mouseMoved()
        super.mouseMoved(x, y)
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
/*?}*/
