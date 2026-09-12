package io.github.fopwoc.mods.hotspot.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.hotspot.client.gui.HotspotScreen
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.config.HotspotConfig

@SideOnly(Side.CLIENT)
object HotspotCommand :
    ClientCommand(
        name = "hotspot",
        usage = "/hotspot | /hotspot profile [seconds] | /hotspot deselect",
    ) {
  override fun run(args: List<String>): String? =
      when (args.firstOrNull()?.lowercase()) {
        null -> {
          ScreenOpener.open(::HotspotScreen)
          null
        }
        "profile" -> {
          val seconds = args.getOrNull(1)?.toIntOrNull() ?: HotspotConfig.defaultDurationSeconds
          ProfileStore.requestProfile(seconds.coerceIn(1, 60) * 20)
          null
        }
        "deselect",
        "clear" -> {
          ProfileStore.clearSelection()
          null
        }
        else -> usage
      }

  override fun complete(args: List<String>): List<String> =
      if (args.size == 1) listOf("profile", "deselect") else emptyList()
}
