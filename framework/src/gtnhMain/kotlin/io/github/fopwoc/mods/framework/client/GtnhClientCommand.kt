package io.github.fopwoc.mods.framework.client

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.ChatComponentText

/** A [ClientCommand] as a 1.7.10 client command. */
@SideOnly(Side.CLIENT)
internal class GtnhClientCommand(private val command: ClientCommand) : CommandBase() {
    override fun getCommandName(): String = command.name

    override fun getCommandUsage(sender: ICommandSender): String = command.usage

    override fun getRequiredPermissionLevel(): Int = 0

    override fun canCommandSenderUseCommand(sender: ICommandSender): Boolean = true

    override fun processCommand(sender: ICommandSender, args: Array<out String>) {
        val minecraft = Minecraft.getMinecraft()
        if (command.requiresWorld && (minecraft.thePlayer == null || minecraft.theWorld == null)) {
            sender.addChatMessage(ChatComponentText("Join a world first to use /${command.name}"))
            return
        }
        command.run(args.toList())?.let { sender.addChatMessage(ChatComponentText(it)) }
    }

    override fun addTabCompletionOptions(sender: ICommandSender, args: Array<out String>): MutableList<String>? {
        val candidates = command.complete(args.toList())
        return if (candidates.isEmpty()) null else getListOfStringsMatchingLastWord(args, *candidates.toTypedArray())
    }
}
