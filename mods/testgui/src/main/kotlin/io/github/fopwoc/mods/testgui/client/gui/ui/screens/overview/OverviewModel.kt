package io.github.fopwoc.mods.testgui.client.gui.ui.screens.overview

import io.github.fopwoc.mods.testgui.client.gui.ui.TestGuiFeature

data class OverviewModel(
    val features: List<TestGuiFeature>,
    val openCount: Int = 0,
    val lastOpenedTitle: String = "Nothing opened yet"
)

