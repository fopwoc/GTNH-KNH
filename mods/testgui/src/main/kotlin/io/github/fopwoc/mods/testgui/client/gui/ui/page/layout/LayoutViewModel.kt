package io.github.fopwoc.mods.testgui.client.gui.ui.page.layout

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class LayoutViewModel : ViewModel() {
  val stateFlow = MutableStateFlow(LayoutModel())

  fun selectTab(tab: LayoutTab) {
    stateFlow.update { state ->
      state.copy(activeTab = tab)
    }
  }

  fun setAlignmentPreset(preset: BoxAlignmentPreset) {
    stateFlow.update { state ->
      state.copy(alignmentPreset = preset)
    }
  }

  fun addScrollChip() {
    stateFlow.update { state ->
      state.copy(scrollChipCount = state.scrollChipCount + 1)
    }
  }

  fun removeScrollChip() {
    stateFlow.update { state ->
      state.copy(scrollChipCount = (state.scrollChipCount - 1).coerceAtLeast(1))
    }
  }
}
