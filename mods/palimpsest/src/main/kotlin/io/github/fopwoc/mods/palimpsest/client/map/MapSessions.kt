package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientWorldContext
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import java.nio.file.Paths
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.world.WorldEvent
import org.apache.logging.log4j.LogManager

/**
 * Opens a [MapSession] for whatever world and dimension the client is in, ticks it, and closes it
 * when the world goes away or the dimension changes.
 */
// The map must never take the client down: every failure is logged and the session dropped.
@Suppress("TooGenericExceptionCaught")
@SideOnly(Side.CLIENT)
object MapSessions {
    private val logger = LogManager.getLogger(MapSessions::class.java)
    private var registered = false
    private var current: MapSession? = null
    private var currentKey: String? = null

    val session: MapSession?
        get() = current

    fun register() {
        if (registered) return
        registered = true
        BlockColors.register()
        FMLCommonHandler.instance().bus().register(this)
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld
        if (world == null || minecraft.thePlayer == null) {
            closeCurrent()
            return
        }
        val worldId = ClientWorldContext.currentId(minecraft) ?: return
        val dimension = world.provider.dimensionId
        val key = "$worldId/dim$dimension"
        if (key != currentKey) {
            closeCurrent()
            open(worldId, dimension, key)
        }
        current?.let { session ->
            try {
                session.tick()
            } catch (failure: Exception) {
                logger.error("Map session failed; closing it", failure)
                closeCurrent()
            }
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        if (event.world.isRemote) closeCurrent()
    }

    private fun open(worldId: String, dimension: Int, key: String) {
        val root = Paths.get(Minecraft.getMinecraft().mcDataDir.path, "palimpsest", "maps", worldId)
        try {
            current = MapSession(root.resolve("dim$dimension"), dimension)
            currentKey = key
            logger.info("Map session opened for {}", key)
        } catch (failure: Exception) {
            logger.error("Could not open map session for {}", key, failure)
            currentKey = key
        }
    }

    private fun closeCurrent() {
        val session = current ?: return
        current = null
        currentKey = null
        try {
            session.close()
        } catch (failure: Exception) {
            logger.error("Closing map session failed", failure)
        }
    }
}
