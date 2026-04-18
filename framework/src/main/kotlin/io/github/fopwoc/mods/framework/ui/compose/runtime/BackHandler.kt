package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

internal class ComposeBackDispatcher {
    private val callbacks = LinkedHashMap<Long, BackCallback>()
    private var nextCallbackId: Long = 1L

    fun dispatchBack(): Boolean {
        val snapshot = callbacks.values.toList().asReversed()
        for (callback in snapshot) {
            if (callback.enabled && callback.onBack()) {
                return true
            }
        }
        return false
    }

    internal fun register(callback: BackCallback): BackCallbackRegistration {
        val callbackId = nextCallbackId++
        callbacks[callbackId] = callback
        return BackCallbackRegistration {
            callbacks.remove(callbackId)
        }
    }
}

internal class BackCallback(
    var enabled: Boolean,
    var onBack: () -> Boolean
)

internal class BackCallbackRegistration(
    private val onDispose: () -> Unit
) {
    fun dispose() {
        onDispose()
    }
}

internal val LocalBackDispatcher = staticCompositionLocalOf<ComposeBackDispatcher?> {
    null
}

@Composable
fun BackHandler(
    enabled: Boolean = true,
    onBack: () -> Boolean
) {
    val dispatcher = LocalBackDispatcher.current ?: return
    val currentOnBack = rememberUpdatedState(onBack)
    val callback = remember {
        BackCallback(
            enabled = enabled,
            onBack = { currentOnBack.value() }
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

