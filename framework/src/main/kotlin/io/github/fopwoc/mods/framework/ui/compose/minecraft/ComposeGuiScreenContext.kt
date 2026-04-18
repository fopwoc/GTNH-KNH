package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime

internal class ComposeGuiScreenContext(
    val runtime: ComposeGuiRuntime
) {
    val hostedWidgets = MinecraftHostedWidgetRegistry()
    val interactionState = ComposeGuiScreenInteractionState(hostedWidgets)
    val renderedInputTargets = mutableListOf<InputTarget>()
}
