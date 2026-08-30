package io.github.fopwoc.mods.testgui.client.gui.ui.page.text

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class TextAndTooltipsViewModel : ViewModel() {
  val stateFlow = MutableStateFlow(TextAndTooltipsModel())

  fun selectTab(tab: TextDemoTab) {
    stateFlow.update { state ->
      state.copy(activeTab = tab)
    }
  }

  fun cycleAccentPreview() {
    stateFlow.update { state ->
      state.copy(accentPasses = state.accentPasses + 1)
    }
  }
}
