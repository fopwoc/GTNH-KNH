package io.github.fopwoc.mods.framework.client

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayerHost
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ModernComposeScreenHost
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.ModernRenderSurface
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * The client side shared by Fabric and NeoForge on Minecraft 26.x. Loaders add only how the one
 * framework HUD element and the chat commands are registered.
 */
abstract class ModernClientBackend : ClientBackend {
    private class Layer(val surface: ModernRenderSurface, val host: HudLayerHost)

    private val layers = mutableListOf<Layer>()
    protected val commands = mutableListOf<ClientCommand>()

    override val isInWorld: Boolean
        get() = Minecraft.getInstance().let { it.player != null && it.level != null }

    override val playerPosition: PlayerPosition?
        get() = Minecraft.getInstance().player?.let { PlayerPosition(it.x, it.y, it.z) }

    override fun openScreen(screen: ComposeScreen) = Minecraft.getInstance().gui.setScreen(ModernComposeScreenHost(screen))

    override fun registerHud(layer: HudLayer) {
        if (layers.isEmpty()) installHud()
        val surface = ModernRenderSurface()
        layers += Layer(surface, HudLayerHost(layer) { surface })
    }

    override fun registerCommand(command: ClientCommand) {
        if (commands.isEmpty()) installCommands()
        commands += command
    }

    /** Registers one HUD element that calls [renderHud] every frame. */
    protected abstract fun installHud()

    /** Arranges for [commands] to be added to the client command tree whenever it is built. */
    protected abstract fun installCommands()

    protected fun renderHud(graphics: GuiGraphicsExtractor) {
        layers.forEach { layer -> layer.surface.drawInto(graphics) { layer.host.render(graphics.guiWidth(), graphics.guiHeight()) } }
    }

    /** [command] as a brigadier tree taking the rest of the line as arguments; [reply] sends chat feedback. */
    protected fun <S> brigadier(command: ClientCommand, reply: (S, String) -> Unit): LiteralArgumentBuilder<S> {
        fun execute(source: S, args: List<String>): Int {
            if (command.requiresWorld && !isInWorld) {
                reply(source, "Join a world first to use /${command.name}")
                return 0
            }
            command.run(args)?.let { reply(source, it) }
            return 1
        }
        return LiteralArgumentBuilder.literal<S>(command.name)
            .executes { execute(it.source, emptyList()) }
            .then(
                RequiredArgumentBuilder.argument<S, String>(ARGS, StringArgumentType.greedyString())
                    .suggests { _, builder ->
                        val typed = builder.remaining.split(' ')
                        val last = typed.last()
                        val offset = builder.createOffset(builder.start + builder.remaining.length - last.length)
                        command.complete(typed).filter { it.startsWith(last, ignoreCase = true) }.forEach(offset::suggest)
                        offset.buildFuture()
                    }
                    .executes { execute(it.source, StringArgumentType.getString(it, ARGS).split(' ').filter(String::isNotEmpty)) },
            )
    }

    private companion object {
        const val ARGS = "args"
    }
}
