package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.MinecraftHostedElementRenderer
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.MinecraftHostedWidgetRegistry
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftPrimitiveRenderCallbacks
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftRenderContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftRenderFrameContext
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.Minecraft

internal abstract class ComposeRenderSession(
    private val content: @Composable () -> Unit
) {
    protected val rootNode = RootNode()
    protected val layoutState = ComposeRenderLayoutState()
    protected val composeRuntime = ComposeGuiRuntime(
        onCompositionChanged = layoutState::invalidateComposition
    )
    protected val runtimeSync = ComposeRenderRuntimeSync(composeRuntime)
    protected val hostedWidgets = MinecraftHostedWidgetRegistry()
    protected val renderedInputTargets = mutableListOf<InputTarget>()

    private var renderEpoch: Int = 0
    private var viewModelOwner: ComposeViewModelOwner? = null

    internal val hasComposition: Boolean
        get() = composeRuntime.isStarted()

    protected fun ensureCompositionCreated() {
        if (composeRuntime.isStarted()) {
            viewModelOwner?.let(::onCompositionReused)
            return
        }

        val owner = ComposeViewModelOwner()
        viewModelOwner = owner
        layoutState.reset()
        owner.onCreate()
        composeRuntime.start(rootNode) {
            ProvideCompositionLocals(owner = owner, content = content)
        }
        owner.onStart()
        owner.onResume()
        composeRuntime.pump()
    }

    protected fun advanceFrame(frameTimeNanos: Long) {
        ensureCompositionCreated()
        runtimeSync.updateScreen(frameTimeNanos)
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
        ensureCompositionCreated()
        runtimeSync.syncBeforeRender()
        layoutState.invalidateComposition()
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
            appendInputTarget = renderedInputTargets::add,
            callbacks = callbacks
        )
        val hostedElementRenderer = MinecraftHostedElementRenderer(
            frame = frame,
            hostedWidgets = hostedWidgets,
            registerInputTarget = renderContext::registerInputTarget,
            focusTextField = focusTextField
        )
        val layoutRoot = layoutState.ensureLayout(rootNode, renderContext, width, height)
        FrameworkRuntimeDebug.captureRenderTree(renderEpoch = renderEpoch, rootNode = rootNode, layoutRoot = layoutRoot)
        try {
            layoutRoot.draw(renderContext, hostedElementRenderer)
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
        FrameworkRuntimeDebug.resetRenderTree()
        hostedWidgets.clear()
        renderedInputTargets.clear()
        renderEpoch = 0
    }

    @Composable
    protected abstract fun ProvideCompositionLocals(
        owner: ComposeViewModelOwner,
        content: @Composable () -> Unit
    )

    protected open fun onCompositionReused(owner: ComposeViewModelOwner) = Unit
}



