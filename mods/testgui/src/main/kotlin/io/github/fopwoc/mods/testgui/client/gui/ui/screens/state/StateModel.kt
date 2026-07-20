package io.github.fopwoc.mods.testgui.client.gui.ui.screens.state

enum class StateMode(val label: String) {
    Live("Live"),
    Replay("Replay"),
    Snapshot("Snapshot")
}

data class StateModel(
    val counter: Int = 0,
    val mode: StateMode = StateMode.Live,
    val viewModelToken: String,
    val eventLog: List<String> = listOf("ViewModel created and ready")
)

