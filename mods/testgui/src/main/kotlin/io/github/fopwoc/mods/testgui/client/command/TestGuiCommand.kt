package io.github.fopwoc.mods.testgui.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.testgui.client.gui.GalleryScreen
import io.github.fopwoc.mods.testgui.client.hud.TestGuiHudOverlay

@SideOnly(Side.CLIENT)
object TestGuiCommand : ClientCommand(name = "testgui", usage = "/testgui | /testgui hud") {
  override fun run(args: List<String>): String? =
      when (args.firstOrNull()?.lowercase()) {
        null -> {
          ScreenOpener.open(::GalleryScreen)
          null
        }
        "hud" -> if (TestGuiHudOverlay.toggle()) "HUD demo on" else "HUD demo off"
        else -> usage
      }

  override fun complete(args: List<String>): List<String> =
      if (args.size == 1) listOf("hud") else emptyList()
}
