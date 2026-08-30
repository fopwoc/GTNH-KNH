package io.github.fopwoc.mods.testgui.client.gui.ui.page.hostedstress

import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

private val defaultListItems =
    listOf(
        "Focus ring alpha",
        "Focus ring beta",
        "Commit gamma",
        "Mirror delta",
        "Overflow epsilon",
        "Stress zeta",
    )

private fun defaultFields(): List<TextFieldState> =
    listOf(
        TextFieldState("Alpha"),
        TextFieldState("Beta"),
        TextFieldState("Gamma"),
        TextFieldState("Delta"),
    )

data class HostedStressModel(
    val fields: List<TextFieldState> = defaultFields(),
    val items: List<String> = defaultListItems,
    val selectedIndex: Int = 0,
    val focusedIndex: Int = 0,
    val commits: Int = 0,
    val mirrorEnabled: Boolean = true,
    val lastSnapshot: String = "No snapshot committed yet",
)
