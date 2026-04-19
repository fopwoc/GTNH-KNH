package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher

internal class ComposeGuiScreenContext {
    val backDispatcher = ComposeBackDispatcher()
    val hostedWidgets = MinecraftHostedWidgetRegistry()
    val interactionState = ComposeGuiScreenInteractionState(hostedWidgets)
    val renderedInputTargets = mutableListOf<InputTarget>()
}
