package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalComposeGuiScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer

internal class ComposeGuiScreenSession(
    private val screen: ComposeGuiScreen,
    private val content: @Composable () -> Unit
) {
    private val rootNode = RootNode()
    private val layoutState = ComposeGuiScreenLayoutState()
    private val composeRuntime = ComposeGuiRuntime(
        onCompositionChanged = {
            layoutState.invalidateComposition()
        }
    )
    private val runtimeSync = ComposeGuiScreenRuntimeSync(composeRuntime)
    private val screenContext = ComposeGuiScreenContext()
    private var viewModelOwner: ComposeScreenViewModelOwner? = null
    private val inputAdapter = ComposeGuiScreenInputAdapter(
        context = screenContext,
        runtimeSync = runtimeSync
    )
    private val frameDispatcher = ComposeGuiScreenFrameDispatcher(
        context = screenContext,
        layoutState = layoutState,
        runtimeSync = runtimeSync
    )

    fun initialize() {
        if (composeRuntime.isStarted()) {
            viewModelOwner?.resumeOnScreen()
            return
        }

        val owner = ComposeScreenViewModelOwner()
        viewModelOwner = owner
        layoutState.reset()
        owner.attachToScreen()
        composeRuntime.start(rootNode) {
            CompositionLocalProvider(
                LocalBackDispatcher provides screenContext.backDispatcher,
                LocalComposeGuiScreen provides screen,
                LocalLifecycleOwner provides owner,
                LocalViewModelStoreOwner provides owner
            ) {
                content()
            }
        }
        owner.showOnScreen()
    }

    fun dispose() {
        viewModelOwner?.clear()
        viewModelOwner = null
        composeRuntime.dispose()

        rootNode.children.clear()
        layoutState.reset()
        screenContext.hostedWidgets.clear()
        screenContext.interactionState.reset()
        frameDispatcher.reset()
    }

    fun keyTyped(typedChar: Char, keyCode: Int, fallback: () -> Unit) {
        inputAdapter.keyTyped(typedChar, keyCode, fallback)
    }

    fun handleMouseInput(width: Int, height: Int, client: Minecraft?, fallback: () -> Unit) {
        inputAdapter.handleMouseInput(width, height, client, fallback)
    }

    fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int, fallback: () -> Unit) {
        inputAdapter.mouseClicked(mouseX, mouseY, mouseButton, fallback)
    }

    fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, fallback: () -> Unit) {
        inputAdapter.mouseClickMove(mouseX, mouseY, clickedMouseButton, fallback)
    }

    fun mouseMovedOrUp(mouseX: Int, mouseY: Int, state: Int, fallback: () -> Unit) {
        inputAdapter.mouseMovedOrUp(mouseX, mouseY, state, fallback)
    }

    fun drawScreen(
        client: Minecraft?,
        font: FontRenderer?,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        drawBackground: () -> Unit,
        drawTooltip: (lines: List<String>, x: Int, y: Int) -> Unit,
        fillRectBlock: (Int, Int, Int, Int, Int) -> Unit,
        drawHorizontalLineBlock: (Int, Int, Int, Int) -> Unit,
        drawVerticalLineBlock: (Int, Int, Int, Int) -> Unit,
        fallback: () -> Unit
    ) {
        frameDispatcher.drawScreen(
            rootNode = rootNode,
            client = client,
            font = font,
            width = width,
            height = height,
            mouseX = mouseX,
            mouseY = mouseY,
            drawBackground = drawBackground,
            drawTooltip = drawTooltip,
            fillRectBlock = fillRectBlock,
            drawHorizontalLineBlock = drawHorizontalLineBlock,
            drawVerticalLineBlock = drawVerticalLineBlock,
            fallback = fallback
        )
    }
}

