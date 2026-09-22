package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime

internal class ComposeRenderRuntimeSync(private val runtime: ComposeGuiRuntime) {
    private var frameDeliveredSinceRender = false

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
        runtime.pump()
        if (!frameDeliveredSinceRender) runtime.sendFrame(System.nanoTime())
        frameDeliveredSinceRender = false
        runtime.pump()
    }

    fun updateScreen(frameTimeNanos: Long) {
        runtime.pump()
        runtime.sendFrame(frameTimeNanos)
        frameDeliveredSinceRender = true
        runtime.pump()
    }

    private fun deliverFrameForStateMutation(frameTimeNanos: Long = System.nanoTime()) {
        runtime.pump()
        runtime.sendFrame(frameTimeNanos)
        frameDeliveredSinceRender = true
        runtime.pump()
    }
}
