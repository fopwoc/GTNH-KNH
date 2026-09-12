package io.github.fopwoc.mods.framework.client

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.ChatComponentText
import net.minecraftforge.client.ClientCommandHandler

/**
 * A client-side chat command: no permission level, replies come back as the return value, tab
 * completion is a list of candidates for the last word.
 *
 * ```kotlin
 * object MyCommand : ClientCommand("mymod", "/mymod | /mymod reset") {
 *   override fun run(args: List<String>): String? = when (args.firstOrNull()) {
 *     null -> { ScreenOpener.open(::MyScreen); null }
 *     "reset" -> MyState.reset().let { "Reset" }
 *     else -> usage
 *   }
 *   override fun complete(args: List<String>) = if (args.size == 1) listOf("reset") else emptyList()
 * }
 * ```
 */
@SideOnly(Side.CLIENT)
abstract class ClientCommand(private val name: String, protected val usage: String) :
    CommandBase() {
  /** Handle the command; a non-null result is sent to chat. [args] never includes the command. */
  protected abstract fun run(args: List<String>): String?

  /** Candidates for the word being typed; filtered by prefix automatically. */
  protected open fun complete(args: List<String>): List<String> = emptyList()

  /** Whether a loaded world is required; the default refuses with a chat message otherwise. */
  protected open val requiresWorld: Boolean = true

  fun register() {
    ClientCommandHandler.instance.registerCommand(this)
  }

  final override fun getCommandName(): String = name

  final override fun getCommandUsage(sender: ICommandSender): String = usage

  final override fun getRequiredPermissionLevel(): Int = 0

  final override fun canCommandSenderUseCommand(sender: ICommandSender): Boolean = true

  final override fun processCommand(sender: ICommandSender, args: Array<out String>) {
    val minecraft = Minecraft.getMinecraft()
    if (requiresWorld && (minecraft.thePlayer == null || minecraft.theWorld == null)) {
      sender.addChatMessage(ChatComponentText("Join a world first to use /$name"))
      return
    }
    run(args.toList())?.let { sender.addChatMessage(ChatComponentText(it)) }
  }

  final override fun addTabCompletionOptions(
      sender: ICommandSender,
      args: Array<out String>,
  ): MutableList<Any?>? {
    val candidates = complete(args.toList())
    return if (candidates.isEmpty()) null
    else getListOfStringsMatchingLastWord(args, *candidates.toTypedArray())
  }
}
