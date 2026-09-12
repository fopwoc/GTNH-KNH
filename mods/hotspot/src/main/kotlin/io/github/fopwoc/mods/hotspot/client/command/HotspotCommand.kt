package io.github.fopwoc.mods.hotspot.client.command

import io.github.fopwoc.mods.hotspot.client.gui.HotspotScreenController
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.config.HotspotConfig
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.ChatComponentText

object HotspotCommand : CommandBase() {
  override fun getCommandName(): String = "hotspot"

  override fun getCommandUsage(sender: ICommandSender): String =
      "/hotspot | /hotspot profile [seconds] | /hotspot deselect"

  override fun getRequiredPermissionLevel(): Int = 0

  override fun canCommandSenderUseCommand(sender: ICommandSender): Boolean = true

  override fun processCommand(sender: ICommandSender, args: Array<out String>) {
    val minecraft = Minecraft.getMinecraft()
    if (minecraft.thePlayer == null || minecraft.theWorld == null) {
      sender.addChatMessage(ChatComponentText("Join a world first to use /hotspot"))
      return
    }
    when (args.firstOrNull()?.lowercase()) {
      null -> HotspotScreenController.requestOpen()
      "profile" -> {
        val seconds = args.getOrNull(1)?.toIntOrNull() ?: HotspotConfig.defaultDurationSeconds
        ProfileStore.requestProfile(seconds.coerceIn(1, 60) * 20)
      }
      "deselect",
      "clear" -> ProfileStore.clearSelection()
      else -> sender.addChatMessage(ChatComponentText(getCommandUsage(sender)))
    }
  }

  override fun addTabCompletionOptions(
      sender: ICommandSender,
      args: Array<out String>,
  ): MutableList<Any?>? =
      if (args.size == 1) getListOfStringsMatchingLastWord(args, "profile", "deselect") else null
}
