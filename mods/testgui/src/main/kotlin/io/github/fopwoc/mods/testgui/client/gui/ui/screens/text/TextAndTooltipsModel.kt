package io.github.fopwoc.mods.testgui.client.gui.ui.screens.text

enum class TextDemoTab(val label: String) {
    Wrapped("Wrapped"),
    Styled("Styled"),
    Tooltips("Tooltips")
}

data class TextAndTooltipsModel(
    val activeTab: TextDemoTab = TextDemoTab.Wrapped,
    val accentPasses: Int = 0
)

