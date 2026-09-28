package io.github.fopwoc.mods.palimpsest.client.prospecting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-long display choices shared by the map and both HUD surfaces. */
object ProspectingLayers {
    private val mutableEnabled = MutableStateFlow(Enabled())
    val enabled = mutableEnabled.asStateFlow()

    fun toggleOre() {
        mutableEnabled.value = mutableEnabled.value.copy(ore = !mutableEnabled.value.ore)
    }

    fun toggleFluid() {
        mutableEnabled.value = mutableEnabled.value.copy(fluid = !mutableEnabled.value.fluid)
    }

    fun toggleNode() {
        mutableEnabled.value = mutableEnabled.value.copy(node = !mutableEnabled.value.node)
    }

    data class Enabled(
        val ore: Boolean = true,
        val fluid: Boolean = true,
        val node: Boolean = true,
    ) {
        fun shows(mark: ProspectingMark): Boolean =
            when (mark.kind) {
                ProspectingMark.Kind.ORE -> ore
                ProspectingMark.Kind.FLUID -> fluid
                ProspectingMark.Kind.NODE -> node
            }
    }
}
