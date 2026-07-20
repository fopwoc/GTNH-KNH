package io.github.fopwoc.mods.testgui.client.gui.ui.screens.controls

enum class ControlsTab(val label: String) {
    Actions("Actions"),
    Selection("Selection"),
    Feedback("Feedback")
}

enum class PowerPreset(
    val label: String,
    val recommendedPower: Double
) {
    Eco("Eco", 28.0),
    Balanced("Balanced", 64.0),
    Overclock("Overclock", 91.0)
}

data class ControlsModel(
    val activeTab: ControlsTab = ControlsTab.Actions,
    val preset: PowerPreset = PowerPreset.Balanced,
    val powerLevel: Double = PowerPreset.Balanced.recommendedPower,
    val automationEnabled: Boolean = false,
    val primaryClicks: Int = 0,
    val styledClicks: Int = 0,
    val lastAction: String = "No control has fired yet"
)

