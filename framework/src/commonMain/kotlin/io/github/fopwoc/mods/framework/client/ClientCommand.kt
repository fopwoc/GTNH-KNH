package io.github.fopwoc.mods.framework.client

/**
 * A client-side chat command: no permission level, replies come back as the return value, tab
 * completion is a list of candidates for the last word. Each platform registers it with its own
 * client command system.
 *
 * ```kotlin
 * object MyCommand : ClientCommand("mymod", "/mymod | /mymod reset") {
 *   override fun run(args: List<String>): String? = when (args.firstOrNull()) {
 *     null -> { Screens.open(MyScreen()); null }
 *     "reset" -> MyState.reset().let { "Reset" }
 *     else -> usage
 *   }
 *   override fun complete(args: List<String>) = if (args.size == 1) listOf("reset") else emptyList()
 * }
 * ```
 */
abstract class ClientCommand(val name: String, val usage: String) {
    /** Handle the command; a non-null result is sent to chat. [args] never includes the command. */
    abstract fun run(args: List<String>): String?

    /** Candidates for the word being typed; filtered by prefix automatically. */
    open fun complete(args: List<String>): List<String> = emptyList()

    /** Whether a loaded world is required; the default refuses with a chat message otherwise. */
    open val requiresWorld: Boolean = true

    fun register() = ClientBackend.current.registerCommand(this)
}
