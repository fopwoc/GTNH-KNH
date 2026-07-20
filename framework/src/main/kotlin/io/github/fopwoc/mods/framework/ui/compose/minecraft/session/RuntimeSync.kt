package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime

internal class ComposeRenderRuntimeSync(
    private val runtime: ComposeGuiRuntime
) {
    fun syncBeforeInput() {
        runtime.pump()
    }

    fun syncAfterHandledInput() {
        deliverFrameForStateMutation()
    }

    fun syncAfterStateMutationIf(changed: Boolean) {
        if (changed) {
            deliverFrameForStateMutation()
        }
    }

    fun syncAfterFallbackIfNeeded() {
        if (runtime.hasPendingNotifications) {
            deliverFrameForStateMutation()
        }
    }

    fun syncBeforeRender() {
        if (runtime.hasPendingNotifications) {
            deliverFrameForStateMutation()
        } else {
            runtime.pump()
        }
    }

    fun updateScreen(frameTimeNanos: Long) {
        runtime.pump()
        runtime.sendFrame(frameTimeNanos)
        runtime.pump()
    }

    private fun deliverFrameForStateMutation(frameTimeNanos: Long = System.nanoTime()) {
        runtime.pump()
        runtime.sendFrame(frameTimeNanos)
        runtime.pump()
    }
}



