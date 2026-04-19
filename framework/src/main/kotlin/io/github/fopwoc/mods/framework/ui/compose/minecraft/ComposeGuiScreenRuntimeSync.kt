package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime

internal class ComposeGuiScreenRuntimeSync(
    private val runtime: ComposeGuiRuntime
) {
    fun syncBeforeInput() {
        runtime.pump()
    }

    fun syncAfterHandledInput() {
        runtime.pump()
    }

    fun syncAfterStateMutationIf(changed: Boolean) {
        if (changed) {
            runtime.pump()
        }
    }

    fun syncAfterFallbackIfNeeded() {
        if (runtime.hasPendingNotifications) {
            runtime.pump()
        }
    }

    fun beginFrame(frameTimeNanos: Long) {
        runtime.pump()
        runtime.sendFrame(frameTimeNanos)
        runtime.pump()
    }
}

