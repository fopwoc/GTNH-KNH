package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeRuntimeErrorHandler
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner

internal abstract class ComposeRenderSession(
    private val surface: RenderSurface,
    private val content: @Composable () -> Unit,
) {
    protected val rootNode = RootNode()
    protected val layoutState = ComposeRenderLayoutState()
    protected val composeRuntime =
        ComposeGuiRuntime(
            onCompositionChanged = layoutState::invalidateComposition,
            errorHandler = ComposeRuntimeErrorHandler(logger::error),
        )
    protected val runtimeSync = ComposeRenderRuntimeSync(composeRuntime)
    protected val renderedInputTargets = mutableListOf<InputTarget>()
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
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        textFieldHost: TextFieldHost,
    ) {
        ensureCompositionCreated()
        composeRuntime.rethrowPendingFailure()
        runtimeSync.syncBeforeRender()
        renderedInputTargets.clear()
        val renderContext =
            surface.beginFrame(width, height, mouseX, mouseY, renderedInputTargets, textFieldHost)
        try {
            layoutState.ensureLayout(rootNode, renderContext, width, height).draw(renderContext)
        } finally {
            surface.endFrame()
        }
    }

    open fun dispose() {
        surface.dispose()
        viewModelOwner?.clear()
        viewModelOwner = null
        composeRuntime.dispose()
        rootNode.children.clear()
        layoutState.reset()
        renderedInputTargets.clear()
    }

    @Composable
    protected abstract fun ProvideCompositionLocals(
        owner: ComposeViewModelOwner,
        content: @Composable () -> Unit,
    )

    protected open fun onCompositionReused(owner: ComposeViewModelOwner) = Unit

    private companion object {
        private val logger = logger<ComposeRenderSession>()
    }
}
