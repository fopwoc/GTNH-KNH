package io.github.fopwoc.mods.framework.ui.compose.runtime

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.MainCoroutineDispatcher

internal object ComposeMainDispatcher : MainCoroutineDispatcher() {
  override val immediate: MainCoroutineDispatcher
    get() = this

  override fun isDispatchNeeded(context: CoroutineContext): Boolean {
    return ComposeMainDispatcherBridge.isDispatchNeeded()
  }

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    ComposeMainDispatcherBridge.dispatch(block)
  }
}
