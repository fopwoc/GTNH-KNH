package io.github.fopwoc.mods.testgui.client.gui.ui.screens.controls

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ControlsViewModel : ViewModel() {
    val stateFlow = MutableStateFlow(ControlsModel())

    fun selectTab(tab: ControlsTab) {
        stateFlow.update { state ->
            state.copy(activeTab = tab)
        }
    }

    fun setPreset(preset: PowerPreset) {
        stateFlow.update { state ->
            state.copy(
                preset = preset,
                powerLevel = preset.recommendedPower,
                lastAction = "Preset set to ${preset.label}"
            )
        }
    }

    fun setPowerLevel(value: Double) {
        stateFlow.update { state ->
            state.copy(
                powerLevel = value,
                lastAction = "Power tuned to ${value.toInt()}%"
            )
        }
    }

    fun toggleAutomation(enabled: Boolean) {
        stateFlow.update { state ->
            state.copy(
                automationEnabled = enabled,
                lastAction = if (enabled) "Automation enabled" else "Automation disabled"
            )
        }
    }

    fun firePrimaryAction() {
        stateFlow.update { state ->
            state.copy(
                primaryClicks = state.primaryClicks + 1,
                lastAction = "Primary native button clicked ${state.primaryClicks + 1} times"
            )
        }
    }

    fun fireStyledAction() {
        stateFlow.update { state ->
            state.copy(
                styledClicks = state.styledClicks + 1,
                lastAction = "Styled action triggered ${state.styledClicks + 1} times"
            )
        }
    }
}

