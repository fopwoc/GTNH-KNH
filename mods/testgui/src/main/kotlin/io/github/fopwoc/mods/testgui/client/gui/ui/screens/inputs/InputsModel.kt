package io.github.fopwoc.mods.testgui.client.gui.ui.screens.inputs

import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

private val defaultInputItems = listOf(
    "Alpha routing",
    "Beta overlays",
    "Gamma tooltips",
    "Delta controls",
    "Epsilon lists",
    "Zeta navigation"
)

data class InputsModel(
    val items: List<String> = defaultInputItems,
    val selectedIndex: Int = 0,
    val fieldState: TextFieldState = TextFieldState(defaultInputItems.first()),
    val lastCommittedText: String = "Nothing committed yet",
    val commitCount: Int = 0,
    val loadCount: Int = 0
)

