package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Intercepts Escape while [enabled]; the innermost registered handler wins. Falls through to the
 * enclosing `NavHost`, then to closing the screen, when nothing consumes it.
 */
@Composable
fun BackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
  BackHandlerResult(enabled = enabled) {
    onBack()
    true
  }
}

/** Like [BackHandler] but the callback decides whether the event was consumed. */
@Composable
fun BackHandlerResult(
    enabled: Boolean = true,
    onBack: () -> Boolean,
) {
  val dispatcher = LocalBackDispatcher.current ?: return
  val currentOnBack = rememberUpdatedState(onBack)
  val callback = remember {
    BackCallback(
        enabled = enabled,
        onBack = { currentOnBack.value() },
    )
  }

  SideEffect {
    callback.enabled = enabled
    callback.onBack = { currentOnBack.value() }
  }

  DisposableEffect(dispatcher, callback) {
    val registration = dispatcher.register(callback)
    onDispose(registration::dispose)
  }
}
