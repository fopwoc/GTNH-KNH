package io.github.fopwoc.mods.testgui.client.gui.ui.screens.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment

enum class LayoutTab(val label: String) {
    Box("Box"),
    Weight("Weight"),
    Scroll("Scroll")
}

enum class BoxAlignmentPreset(
    val label: String,
    val alignment: Alignment
) {
    TopStart("Top start", Alignment.TopStart),
    Center("Center", Alignment.Center),
    BottomEnd("Bottom end", Alignment.BottomEnd)
}

data class LayoutModel(
    val activeTab: LayoutTab = LayoutTab.Box,
    val alignmentPreset: BoxAlignmentPreset = BoxAlignmentPreset.Center,
    val scrollChipCount: Int = 10
)

