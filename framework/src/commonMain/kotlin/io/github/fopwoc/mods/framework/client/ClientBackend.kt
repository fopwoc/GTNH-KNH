package io.github.fopwoc.mods.framework.client

import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
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

    /** Shows [screen] now; [Screens] calls this on the client thread between ticks. */
    fun openScreen(screen: ComposeScreen)

    fun registerHud(layer: HudLayer)

    fun registerCommand(command: ClientCommand)

    companion object {
        val current: ClientBackend by lazy {
            checkNotNull(ServiceLoader.load(ClientBackend::class.java, ClientBackend::class.java.classLoader).firstOrNull()) {
                "No KNH Core client backend; client APIs are unavailable on a dedicated server"
            }
        }
    }
}
