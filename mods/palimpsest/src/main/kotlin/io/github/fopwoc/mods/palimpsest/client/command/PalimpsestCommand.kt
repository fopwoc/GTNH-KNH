package io.github.fopwoc.mods.palimpsest.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.palimpsest.client.gui.PalimpsestScreen

@SideOnly(Side.CLIENT)
object PalimpsestCommand : ClientCommand(name = "palimpsest", usage = "/palimpsest") {
  override fun run(args: List<String>): String? {
    if (args.isNotEmpty()) return usage
    ScreenOpener.open(::PalimpsestScreen)
    return null
  }
}
