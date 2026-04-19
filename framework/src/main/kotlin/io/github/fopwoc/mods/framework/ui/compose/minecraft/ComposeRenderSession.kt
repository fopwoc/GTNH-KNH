package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.Minecraft

internal abstract class ComposeRenderSession(
    private val content: @Composable () -> Unit
) {
    protected val rootNode = RootNode()
    protected val layoutState = ComposeGuiScreenLayoutState()
    protected val composeRuntime = ComposeGuiRuntime(
        onCompositionChanged = layoutState::invalidateComposition
    )
    protected val runtimeSync = ComposeGuiScreenRuntimeSync(composeRuntime)
    protected val hostedWidgets = MinecraftHostedWidgetRegistry()
    protected val renderedInputTargets = mutableListOf<InputTarget>()

    private var renderEpoch: Int = 0
    private var viewModelOwner: ComposeScreenViewModelOwner? = null

    protected fun ensureStarted() {
        if (composeRuntime.isStarted()) {
            viewModelOwner?.let(::onSessionResumed)
            return
        }

        val owner = ComposeScreenViewModelOwner()
        viewModelOwner = owner
        layoutState.reset()
        owner.attachToScreen()
        composeRuntime.start(rootNode) {
            ProvideSessionContent(owner = owner, content = content)
        }
        owner.showOnScreen()
    }

    protected fun renderComposeTree(
        client: Minecraft,
        font: FontRenderer,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        focusTextField: (TextFieldState) -> Unit,
        callbacks: MinecraftPrimitiveRenderCallbacks
    ) {
        ensureStarted()
        runtimeSync.beginFrame(System.nanoTime())
        renderEpoch += 1
        renderedInputTargets.clear()

        val frame = MinecraftRenderFrameContext(
            client = client,
            font = font,
            viewportWidth = width,
            viewportHeight = height,
            mouseX = mouseX,
            mouseY = mouseY,
            renderEpoch = renderEpoch
        )
        val renderContext = MinecraftRenderContext(
            frame = frame,
            hostedWidgets = hostedWidgets,
            appendInputTarget = renderedInputTargets::add,
            focusTextField = focusTextField,
            callbacks = callbacks
        )
        try {
            layoutState.ensureLayout(rootNode, renderContext, width, height).draw(renderContext)
        } finally {
            renderContext.resetClipState()
        }

        hostedWidgets.prune(renderEpoch)
    }

    open fun dispose() {
        viewModelOwner?.clear()
        viewModelOwner = null
        composeRuntime.dispose()
        rootNode.children.clear()
        layoutState.reset()
        hostedWidgets.clear()
        renderedInputTargets.clear()
        renderEpoch = 0
    }

    @Composable
    protected abstract fun ProvideSessionContent(
        owner: ComposeScreenViewModelOwner,
        content: @Composable () -> Unit
    )

    protected open fun onSessionResumed(owner: ComposeScreenViewModelOwner) = Unit
}

