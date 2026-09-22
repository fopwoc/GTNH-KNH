package io.github.fopwoc.mods.framework.ui.compose.runtime

internal fun interface ComposeRuntimeErrorHandler {
    fun report(message: String, throwable: Throwable)
}
