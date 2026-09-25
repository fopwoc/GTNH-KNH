package io.github.fopwoc.mods.framework.client

import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen
import java.util.ServiceLoader

/**
 * The physical client's UI integration: screens, HUD layers, chat commands and the local player.
 * Each platform source set registers one in `META-INF/services`; it is only ever loaded on the
 * client.
 */
interface ClientBackend {
    /** Whether a world is loaded and the player is in it. */
    val isInWorld: Boolean

    /** The local player's position, or null outside a world. */
    val playerPosition: PlayerPosition?

    /**
     * String form of the current world's ID, numeric on GTNH and namespaced on modern Minecraft.
     */
    val currentDimensionId: String?

    /**
     * Stable, file-name-safe id of where the client is: `singleplayer-<save>` or
     * `server-<address>`. Mods key per-world client state (saved measurements, maps) on it; null
     * outside a world.
     */
    val currentWorldId: String?

    val isPlayerListOpen: Boolean

    /** Bounds of the player list where known, in GUI-scaled pixels. */
    fun playerListBounds(screenWidth: Int): HudRect?

    fun textWidth(text: String): Int

    fun trimTextToWidth(text: String, width: Int): String

    /** Shows [screen] now; [Screens] calls this on the client thread between ticks. */
    fun openScreen(screen: ComposeScreen)

    fun registerHud(layer: HudLayer)

    fun registerCommand(command: ClientCommand)

    fun registerKeyBinding(binding: KeyBinding)

    fun isBindingDown(binding: KeyBinding): Boolean

    fun bindingMatches(binding: KeyBinding, press: KeyPress): Boolean

    fun isKeyDown(key: Key): Boolean

    /**
     * The mouse in GUI-scaled coordinates with sub-pixel precision; sampled any time, not per
     * event.
     */
    val pointerX: Double
    val pointerY: Double

    /** Button 0 is left, 1 right, 2 middle. */
    fun isMouseButtonDown(button: Int): Boolean

    companion object {
        val current: ClientBackend by lazy {
            checkNotNull(
                ServiceLoader.load(ClientBackend::class.java, ClientBackend::class.java.classLoader)
                    .firstOrNull()
            ) {
                "No KNH Core client backend; client APIs are unavailable on a dedicated server"
            }
        }
    }
}
