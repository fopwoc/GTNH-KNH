package io.github.fopwoc.mods.gtnhclientworldbackup.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhclientworldbackup.client.gui.BackupStatusScreenController
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.ChatComponentText

@SideOnly(Side.CLIENT)
class BackupStatusCommand : CommandBase() {
    override fun getCommandName(): String = COMMAND_NAME

    override fun getCommandUsage(sender: ICommandSender): String = "/$COMMAND_NAME"

    override fun getCommandAliases(): MutableList<String> = COMMAND_ALIASES.toMutableList()

    override fun canCommandSenderUseCommand(sender: ICommandSender): Boolean = true

    override fun processCommand(sender: ICommandSender, args: Array<String>) {
        if (args.isNotEmpty()) {
            sender.addChatMessage(ChatComponentText("$CHAT_PREFIX Usage: ${getCommandUsage(sender)}"))
            return
        }

        val minecraft = Minecraft.getMinecraft()
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            sender.addChatMessage(ChatComponentText("$CHAT_PREFIX Join a world before opening the backup GUI."))
            return
        }

        BackupStatusScreenController.requestOpen()
    }

    companion object {
        const val COMMAND_NAME = "backupgui"
        private const val CHAT_PREFIX = "[Observed Backup]"
        val COMMAND_ALIASES: List<String> = listOf("backupstatus", "observedbackup", "obbackup")
    }
}


