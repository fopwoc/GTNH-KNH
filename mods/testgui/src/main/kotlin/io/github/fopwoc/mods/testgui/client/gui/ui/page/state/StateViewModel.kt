package io.github.fopwoc.mods.testgui.client.gui.ui.page.state

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class StateViewModel : ViewModel() {
  val stateFlow = MutableStateFlow(StateModel(viewModelToken = nextToken().toString()))

  fun increment() {
    stateFlow.update { state ->
      state.copy(
          counter = state.counter + 1,
          eventLog = prepend("Counter incremented to ${state.counter + 1}", state.eventLog),
      )
    }
  }

  fun decrement() {
    stateFlow.update { state ->
      state.copy(
          counter = state.counter - 1,
          eventLog = prepend("Counter decremented to ${state.counter - 1}", state.eventLog),
      )
    }
  }

  fun setMode(mode: StateMode) {
    stateFlow.update { state ->
      state.copy(
          mode = mode,
          eventLog = prepend("Mode switched to ${mode.label}", state.eventLog),
      )
    }
  }

  fun recordCoverNavigation() {
    stateFlow.update { state ->
      state.copy(
          eventLog =
              prepend("Opened another top-level destination to cover State Lab", state.eventLog)
      )
    }
  }

  private fun prepend(entry: String, log: List<String>): List<String> = listOf(entry) + log.take(5)

  private companion object {
    var nextToken: Int = 1

    fun nextToken(): Int = nextToken++
  }
}
