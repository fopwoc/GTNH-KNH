package io.github.fopwoc.mods.testgui.client.command

import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.ui.compose.screen.Screens
import io.github.fopwoc.mods.testgui.client.gui.GalleryScreen
import io.github.fopwoc.mods.testgui.client.hud.TestGuiHudOverlay

object TestGuiCommand : ClientCommand(name = "testgui", usage = "/testgui | /testgui hud") {
    override fun run(args: List<String>): String? =
        when (args.firstOrNull()?.lowercase()) {
            null -> {
                Screens.open(GalleryScreen())
                null
            }
            "hud" -> if (TestGuiHudOverlay.toggle()) "HUD demo on" else "HUD demo off"
            else -> usage
        }

    override fun complete(args: List<String>): List<String> = if (args.size == 1) listOf("hud") else emptyList()
}
