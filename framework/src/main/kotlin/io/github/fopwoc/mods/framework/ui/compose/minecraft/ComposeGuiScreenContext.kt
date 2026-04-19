package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher

internal class ComposeGuiScreenContext(
    val hostedWidgets: MinecraftHostedWidgetRegistry,
    val renderedInputTargets: MutableList<InputTarget>
) {
    val backDispatcher = ComposeBackDispatcher()
    val interactionState = ComposeGuiScreenInteractionState(hostedWidgets)
}
