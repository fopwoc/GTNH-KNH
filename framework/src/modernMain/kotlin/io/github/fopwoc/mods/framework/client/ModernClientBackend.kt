package io.github.fopwoc.mods.framework.client

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayerHost
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.GuiDrawing
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.ModernRenderSurface
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.glfwCode
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import org.lwjgl.glfw.GLFW

/**
 * The client side shared by Fabric and NeoForge on every modern Minecraft version. Loaders add only
 * how the one framework HUD element, key mappings and the chat commands are registered.
 */
abstract class ModernClientBackend : ClientBackend {
    private class Layer(val surface: ModernRenderSurface, val host: HudLayerHost)

    private val layers = mutableListOf<Layer>()
    protected val commands = mutableListOf<ClientCommand>()

    override val isInWorld: Boolean
        get() = Minecraft.getInstance().let { it.player != null && it.level != null }

    override val playerPosition: PlayerPosition?
        get() = Minecraft.getInstance().player?.let { PlayerPosition(it.x, it.y, it.z) }

    override val currentDimensionId: String?
        get() {
            val dimension = Minecraft.getInstance().level?.dimension() ?: return null
            /*? if >=26 {*/
            return dimension.identifier().toString()
            /*?} else {*/
            /*return dimension.location().toString()
             */
            /*?}*/
        }

    override val currentWorldId: String?
        get() {
            val client = Minecraft.getInstance()
            if (client.level == null) return null
            val descriptor =
                if (client.isLocalServer) client.singleplayerServer?.worldData?.levelName
                else client.currentServer?.ip
            val kind = if (client.isLocalServer) "singleplayer" else "server"
            return "$kind-${(descriptor?.takeIf(String::isNotBlank) ?: "world").replace(Regex("[^A-Za-z0-9._-]"), "_")}"
        }

    override val isPlayerListOpen: Boolean
        get() {
            val minecraft = Minecraft.getInstance()
            /*? if >=26 {*/
            val hidden = minecraft.gui.hud.isHidden()
            /*?} else {*/
            /*val hidden = minecraft.options.hideGui
             */
            /*?}*/
            if (!minecraft.options.keyPlayerList.isDown || hidden) return false
            val player = minecraft.player ?: return false
            val level = minecraft.level ?: return false
            val objective = level.scoreboard.getDisplayObjective(DisplaySlot.LIST)
            return !minecraft.isLocalServer ||
                player.connection.getListedOnlinePlayers().size > 1 ||
                objective != null
        }

    override fun playerListBounds(screenWidth: Int): HudRect? = null

    override fun textWidth(text: String): Int = Minecraft.getInstance().font.width(text)

    override fun trimTextToWidth(text: String, width: Int): String =
        Minecraft.getInstance().font.plainSubstrByWidth(text, width)

    override fun openScreen(screen: ComposeScreen) {
        /*? if >=26 {*/
        Minecraft.getInstance()
            .gui
            .setScreen(
                io.github.fopwoc.mods.framework.ui.compose.minecraft.ModernComposeScreenHost(screen)
            )
        /*?} else {*/
        /*Minecraft.getInstance()
           .setScreen(
               io.github.fopwoc.mods.framework.ui.compose.minecraft.LegacyComposeScreenHost(screen)
           )
        */
        /*?}*/
    }

    @Synchronized
    override fun registerHud(layer: HudLayer) {
        if (layers.isEmpty()) installHud()
        val surface = ModernRenderSurface()
        layers += Layer(surface, HudLayerHost(layer) { surface })
    }

    @Synchronized
    override fun registerCommand(command: ClientCommand) {
        if (commands.isEmpty()) installCommands()
        commands += command
    }

    private val bindings = LinkedHashMap<KeyBinding, KeyMapping>()
    private val categories = LinkedHashMap<String, KeyCategory>()

    @Synchronized
    override fun registerKeyBinding(binding: KeyBinding) {
        if (bindings.isEmpty()) ClientEvents.tickEnd.subscribe { pollBindings() }
        val category = categories.getOrPut(binding.category) { keyCategory(binding.category) }
        val mapping =
            KeyMapping(
                binding.name,
                InputConstants.Type.KEYSYM,
                glfwCode(binding.defaultKey ?: Key.Unknown),
                category,
            )
        bindings[binding] = mapping
        registerKeyMapping(mapping, category)
    }

    override fun isBindingDown(binding: KeyBinding): Boolean = mapping(binding).isDown

    override fun bindingMatches(binding: KeyBinding, press: KeyPress): Boolean =
        boundKey(mapping(binding)).let {
            it.type == InputConstants.Type.KEYSYM &&
                it.value == press.code &&
                it.value != InputConstants.UNKNOWN.value
        }

    override fun isKeyDown(key: Key): Boolean =
        glfwCode(key).let {
            /*? if >=26 {*/
            it >= 0 && InputConstants.isKeyDown(Minecraft.getInstance().window, it)
            /*?} else {*/
            /*it >= 0 && InputConstants.isKeyDown(Minecraft.getInstance().window.window, it)
             */
            /*?}*/
        }

    /*? if >=26 {*/
    override val pointerX: Double
        get() = Minecraft.getInstance().let { it.mouseHandler.getScaledXPos(it.window) }

    override val pointerY: Double
        get() = Minecraft.getInstance().let { it.mouseHandler.getScaledYPos(it.window) }

    override fun isMouseButtonDown(button: Int): Boolean =
        GLFW.glfwGetMouseButton(Minecraft.getInstance().window.handle(), button) == GLFW.GLFW_PRESS

    /*?} else {*/
    /*override val pointerX: Double
        get() =
            Minecraft.getInstance().let {
                it.mouseHandler.xpos() * it.window.guiScaledWidth / it.window.screenWidth
            }

    override val pointerY: Double
        get() =
            Minecraft.getInstance().let {
                it.mouseHandler.ypos() * it.window.guiScaledHeight / it.window.screenHeight
            }

    override fun isMouseButtonDown(button: Int): Boolean =
        GLFW.glfwGetMouseButton(Minecraft.getInstance().window.window, button) == GLFW.GLFW_PRESS

    */
    /*?}*/

    private fun mapping(binding: KeyBinding) =
        checkNotNull(bindings[binding]) { "Key binding ${binding.name} is not registered" }

    private fun pollBindings() {
        bindings.forEach { (binding, mapping) ->
            while (mapping.consumeClick()) binding.onPress()
        }
    }

    /**
     * Registers [mapping] with the loader, together with its [category] the first time it is seen.
     */
    protected abstract fun registerKeyMapping(mapping: KeyMapping, category: KeyCategory)

    /** The key [mapping] is bound to after the player's rebinding. */
    protected abstract fun boundKey(mapping: KeyMapping): InputConstants.Key

    /** Registers one HUD element that calls [renderHud] every frame. */
    protected abstract fun installHud()

    /** Arranges for [commands] to be added to the client command tree whenever it is built. */
    protected abstract fun installCommands()

    protected fun renderHud(graphics: GuiDrawing) {
        layers.forEach { layer ->
            layer.surface.drawInto(graphics) {
                layer.host.render(graphics.guiWidth(), graphics.guiHeight())
            }
        }
    }

    /**
     * [command] as a brigadier tree taking the rest of the line as arguments; [reply] sends chat
     * feedback.
     */
    protected fun <S> brigadier(
        command: ClientCommand,
        reply: (S, String) -> Unit,
    ): LiteralArgumentBuilder<S> {
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
                        val offset =
                            builder.createOffset(
                                builder.start + builder.remaining.length - last.length
                            )
                        command
                            .complete(typed)
                            .filter { it.startsWith(last, ignoreCase = true) }
                            .forEach(offset::suggest)
                        offset.buildFuture()
                    }
                    .executes {
                        execute(
                            it.source,
                            StringArgumentType.getString(it, ARGS)
                                .split(' ')
                                .filter(String::isNotEmpty),
                        )
                    }
            )
    }

    private companion object {
        const val ARGS = "args"
    }
}
