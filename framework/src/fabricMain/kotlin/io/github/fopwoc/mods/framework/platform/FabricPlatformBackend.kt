package io.github.fopwoc.mods.framework.platform

import io.github.fopwoc.mods.framework.event.ServerEvents
import io.github.fopwoc.mods.framework.player.GamePlayer
import java.io.File
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer

class FabricPlatformBackend : PlatformBackend {
    private val fabric = FabricLoader.getInstance()

    override val loader = Loader.FABRIC
    override val minecraftVersion: String =
        fabric.getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("unknown")
    override val isClient: Boolean get() = fabric.environmentType == EnvType.CLIENT
    override val gameDirectory: File get() = fabric.gameDir.toFile()
    override val configDirectory: File get() = fabric.configDir.toFile()

    override fun isModLoaded(modId: String): Boolean = fabric.isModLoaded(modId)

    override fun installEvents() {
        ServerTickEvents.START_SERVER_TICK.register { ServerEvents.tickStart.emit(Unit) }
        ServerTickEvents.END_SERVER_TICK.register { ServerEvents.tickEnd.emit(Unit) }
        ServerLifecycleEvents.SERVER_STARTED.register { ServerEvents.started.emit(Unit) }
        ServerLifecycleEvents.SERVER_STOPPING.register { ServerEvents.stopping.emit(Unit) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> ServerEvents.playerJoined.emit(handler.player.toGamePlayer()) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> ServerEvents.playerLeft.emit(handler.player.toGamePlayer()) }
        if (isClient) FabricClientEvents.install()
    }
}

fun ServerPlayer.toGamePlayer(): GamePlayer = GamePlayer(uuid, name.string)
