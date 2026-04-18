package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalComposeGuiScreen
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
abstract class ComposeGuiScreen : GuiScreen() {
    private val rootNode = RootNode()
    private val layoutState = ComposeGuiScreenLayoutState()
    private val composeRuntime = ComposeGuiRuntime(
        onCompositionChanged = {
            layoutState.invalidateComposition()
        }
    )
    private val screenContext = ComposeGuiScreenContext(composeRuntime)
    private val inputAdapter = ComposeGuiScreenInputAdapter(screenContext)
    private val frameDispatcher = ComposeGuiScreenFrameDispatcher(
        context = screenContext,
        layoutState = layoutState,
    )

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

        if (composeRuntime.isStarted()) {
            return
        }

        layoutState.reset()
        composeRuntime.start(rootNode) {
            CompositionLocalProvider(LocalComposeGuiScreen provides this@ComposeGuiScreen) {
                Content()
            }
        }
    }

    override fun onGuiClosed() {
        composeRuntime.dispose()

        rootNode.children.clear()
        layoutState.reset()
        screenContext.hostedWidgets.clear()
        screenContext.interactionState.reset()
        frameDispatcher.reset()
        super.onGuiClosed()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        inputAdapter.keyTyped(typedChar, keyCode) {
            super.keyTyped(typedChar, keyCode)
        }
    }

    override fun handleMouseInput() {
        inputAdapter.handleMouseInput(width, height, mc) {
            super.handleMouseInput()
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        inputAdapter.mouseClicked(mouseX, mouseY, mouseButton) {
            super.mouseClicked(mouseX, mouseY, mouseButton)
        }
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        inputAdapter.mouseClickMove(mouseX, mouseY, clickedMouseButton) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
        }
    }

    override fun mouseMovedOrUp(mouseX: Int, mouseY: Int, state: Int) {
        inputAdapter.mouseMovedOrUp(mouseX, mouseY, state) {
            super.mouseMovedOrUp(mouseX, mouseY, state)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        frameDispatcher.drawScreen(
            rootNode = rootNode,
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
