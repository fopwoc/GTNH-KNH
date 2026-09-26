package io.github.fopwoc.mods.framework.platform

import io.github.fopwoc.mods.framework.event.ServerEvents
import io.github.fopwoc.mods.framework.player.GamePlayer
import java.io.File
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

class NeoForgePlatformBackend : PlatformBackend {
    override val loader = Loader.NEOFORGE
    override val minecraftVersion: String =
        ModList.get()
            .getModContainerById("minecraft")
            .map { it.modInfo.version.toString() }
            .orElse("unknown")
    override val isClient: Boolean
        get() = isPhysicalClient

    override val gameDirectory: File
        get() = FMLPaths.GAMEDIR.get().toFile()

    override val configDirectory: File
        get() = FMLPaths.CONFIGDIR.get().toFile()

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)

    override fun installEvents() {
        val bus = NeoForge.EVENT_BUS
        bus.addListener(ServerTickEvent.Pre::class.java) { ServerEvents.tickStart.emit(Unit) }
        bus.addListener(ServerTickEvent.Post::class.java) { ServerEvents.tickEnd.emit(Unit) }
        bus.addListener(ServerStartedEvent::class.java) { ServerEvents.started.emit(Unit) }
        bus.addListener(ServerStoppingEvent::class.java) { ServerEvents.stopping.emit(Unit) }
        bus.addListener(PlayerEvent.PlayerLoggedInEvent::class.java) {
            ServerEvents.playerJoined.emit(it.entity.toGamePlayer())
        }
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent::class.java) {
            ServerEvents.playerLeft.emit(it.entity.toGamePlayer())
        }
        if (isClient) NeoForgeClientEvents.install()
    }
}

fun Player.toGamePlayer(): GamePlayer = GamePlayer(uuid, name.string)

/** Whether this is the physical client; FML turned its static field into a getter in 26.x. */
internal val isPhysicalClient: Boolean
    /*? if >=26 {*/
    get() = FMLEnvironment.getDist().isClient
/*?} else {*/
/*get() = FMLEnvironment.dist.isClient
 */
/*?}*/
