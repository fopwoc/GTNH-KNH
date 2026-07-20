package io.github.fopwoc.mods.testgui.client.gui.ui.screens.scrollclipstress

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ScrollClipStressViewModel : ViewModel() {
    val stateFlow = MutableStateFlow(ScrollClipStressModel())

    fun addLane() {
        stateFlow.update { state ->
            state.copy(laneCount = state.laneCount + 1)
        }
    }

    fun removeLane() {
        stateFlow.update { state ->
            state.copy(laneCount = (state.laneCount - 1).coerceAtLeast(4))
        }
    }

    fun increaseBadgeOffset() {
        stateFlow.update { state ->
            state.copy(badgeOffset = (state.badgeOffset + 4).coerceAtMost(36))
        }
    }

    fun decreaseBadgeOffset() {
        stateFlow.update { state ->
            state.copy(badgeOffset = (state.badgeOffset - 4).coerceAtLeast(0))
        }
    }

    fun toggleCompactMode(enabled: Boolean) {
        stateFlow.update { state ->
            state.copy(compactMode = enabled)
        }
    }

    fun toggleAlternatingBadges(enabled: Boolean) {
        stateFlow.update { state ->
            state.copy(alternatingBadges = enabled)
        }
    }
}

