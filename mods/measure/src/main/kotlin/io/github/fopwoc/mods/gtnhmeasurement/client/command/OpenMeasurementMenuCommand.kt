package io.github.fopwoc.mods.gtnhmeasurement.client.command

import io.github.fopwoc.mods.gtnhmeasurement.client.gui.MeasurementScreenController
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.ChatComponentText

object OpenMeasurementMenuCommand : CommandBase() {
  override fun getCommandName(): String = "measure"

  override fun getCommandUsage(sender: ICommandSender): String = "/measure"

  override fun getRequiredPermissionLevel(): Int = 0

  override fun canCommandSenderUseCommand(sender: ICommandSender): Boolean = true

  override fun processCommand(sender: ICommandSender, args: Array<out String>) {
    val minecraft = Minecraft.getMinecraft()
    if (minecraft.thePlayer == null || minecraft.theWorld == null) {
      sender.addChatMessage(ChatComponentText("Open a world first to use /measure"))
      return
    }

    MeasurementScreenController.requestOpen()
  }
}
