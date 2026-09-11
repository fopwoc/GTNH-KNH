package io.github.fopwoc.mods.gtnhmeasurement.client.command

import io.github.fopwoc.mods.gtnhmeasurement.client.gui.MeasurementScreenController
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementExchange
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementPersistence
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.ChatComponentText

object OpenMeasurementMenuCommand : CommandBase() {
  override fun getCommandName(): String = "measure"

  override fun getCommandUsage(sender: ICommandSender): String =
      "/measure | /measure export <name> | /measure import <name> | /measure exports"

  override fun getRequiredPermissionLevel(): Int = 0

  override fun canCommandSenderUseCommand(sender: ICommandSender): Boolean = true

  override fun processCommand(sender: ICommandSender, args: Array<out String>) {
    val minecraft = Minecraft.getMinecraft()
    if (minecraft.thePlayer == null || minecraft.theWorld == null) {
      sender.addChatMessage(ChatComponentText("Open a world first to use /measure"))
      return
    }

    val reply =
        when (args.firstOrNull()?.lowercase()) {
          null -> {
            MeasurementScreenController.requestOpen()
            return
          }
          "export" -> MeasurementExchange.export(args.drop(1).joinToString(" "))
          "import" -> MeasurementExchange.import(args.drop(1).joinToString(" "))
          "exports" -> MeasurementExchange.list()
          else -> getCommandUsage(sender)
        }
    sender.addChatMessage(ChatComponentText(reply))
  }

  override fun addTabCompletionOptions(
      sender: ICommandSender,
      args: Array<out String>,
  ): List<String>? =
      when (args.size) {
        1 -> getListOfStringsMatchingLastWord(args, "export", "import", "exports")
        2 ->
            if (args[0].equals("import", ignoreCase = true)) {
              getListOfStringsMatchingLastWord(
                  args,
                  *MeasurementPersistence.listExports().toTypedArray(),
              )
            } else {
              null
            }
        else -> null
      }
}
