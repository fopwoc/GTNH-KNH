package io.github.fopwoc.mods.palimpsest.client.claim

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared session-long visibility choice for the full map and minimap. */
object ClaimLayer {
    private val mutableEnabled = MutableStateFlow(true)
    val enabled = mutableEnabled.asStateFlow()

    fun toggle() {
        mutableEnabled.value = !mutableEnabled.value
    }
}
