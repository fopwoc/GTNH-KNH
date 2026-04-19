package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.fopwoc.mods.framework.ui.compose.layout.InputDispatcher
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalComposeGuiScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer

internal class ComposeGuiScreenSession(
    private val screen: ComposeGuiScreen,
    content: @Composable () -> Unit
) : ComposeRenderSession(content) {
    private val screenContext = ComposeGuiScreenContext(
        hostedWidgets = hostedWidgets,
        renderedInputTargets = renderedInputTargets
    )
    private val inputAdapter = ComposeGuiScreenInputAdapter(
        context = screenContext,
        runtimeSync = runtimeSync
    )

    fun initialize() {
        ensureStarted()
    }

    override fun dispose() {
        super.dispose()
        screenContext.interactionState.reset()
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
        partialTicks: Float,
        callbacks: ComposeGuiScreenRenderCallbacks
    ) {
        callbacks.drawBackground()

        val resolvedClient = client ?: run {
            callbacks.drawFallback(mouseX, mouseY, partialTicks)
            return
        }
        val resolvedFont = font ?: run {
            callbacks.drawFallback(mouseX, mouseY, partialTicks)
            return
        }

        renderComposeTree(
            client = resolvedClient,
            font = resolvedFont,
            width = width,
            height = height,
            mouseX = mouseX,
            mouseY = mouseY,
            focusTextField = screenContext.interactionState::focusTextField,
            callbacks = callbacks
        )

        screenContext.interactionState.refreshAfterRender()
        val hoveredTooltip = InputDispatcher.findTopmostTooltipTarget(renderedInputTargets, mouseX, mouseY)?.tooltipLines
        callbacks.drawFallback(mouseX, mouseY, partialTicks)
        hoveredTooltip?.let { lines ->
            callbacks.drawTooltip(lines, mouseX, mouseY)
        }
    }

    @Composable
    override fun ProvideSessionContent(
        owner: ComposeScreenViewModelOwner,
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(
            LocalBackDispatcher provides screenContext.backDispatcher,
            LocalComposeGuiScreen provides screen,
            LocalLifecycleOwner provides owner,
            LocalViewModelStoreOwner provides owner
        ) {
            content()
        }
    }

    override fun onSessionResumed(owner: ComposeScreenViewModelOwner) {
        owner.resumeOnScreen()
    }
}

