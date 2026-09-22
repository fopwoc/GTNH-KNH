package io.github.fopwoc.mods.testgui.client.gui.ui.page.gallery

import androidx.compose.runtime.Composable

/** One gallery entry: a component or behaviour shown in its meaningful states. */
data class Story(
    val title: String,
    val content: @Composable () -> Unit,
)
