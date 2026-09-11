package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftPrimitiveRenderCallbacks
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftRenderContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftRenderFrameContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.TextWrapCache
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer

internal abstract class ComposeRenderSession(private val content: @Composable () -> Unit) {
  protected val rootNode = RootNode()
  protected val layoutState = ComposeRenderLayoutState()
  protected val composeRuntime =
      ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
  protected val runtimeSync = ComposeRenderRuntimeSync(composeRuntime)
  protected val renderedInputTargets = mutableListOf<InputTarget>()
  private val wrapCache = TextWrapCache()

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
      textFieldHost: TextFieldHost,
      callbacks: MinecraftPrimitiveRenderCallbacks,
  ) {
    ensureCompositionCreated()
    composeRuntime.rethrowPendingFailure()
    runtimeSync.syncBeforeRender()
    renderEpoch += 1
    renderedInputTargets.clear()

    val frame =
        MinecraftRenderFrameContext(
            client = client,
            font = font,
            viewportWidth = width,
            viewportHeight = height,
            mouseX = mouseX,
            mouseY = mouseY,
            renderEpoch = renderEpoch,
        )
    val renderContext =
        MinecraftRenderContext(
            frame = frame,
            appendInputTarget = renderedInputTargets::add,
            callbacks = callbacks,
            wrapCache = wrapCache,
            textFields = textFieldHost,
        )
    val layoutRoot = layoutState.ensureLayout(rootNode, renderContext, width, height)
    try {
      layoutRoot.draw(renderContext)
    } finally {
      renderContext.resetClipState()
    }
  }

  open fun dispose() {
    viewModelOwner?.clear()
    viewModelOwner = null
    composeRuntime.dispose()
    rootNode.children.clear()
    layoutState.reset()
    renderedInputTargets.clear()
    renderEpoch = 0
  }

  @Composable
  protected abstract fun ProvideCompositionLocals(
      owner: ComposeViewModelOwner,
      content: @Composable () -> Unit,
  )

  protected open fun onCompositionReused(owner: ComposeViewModelOwner) = Unit
}
