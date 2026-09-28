package io.github.fopwoc.mods.palimpsest.client.claim

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import serverutils.events.chunks.UpdateClientDataEvent
import serverutils.net.MessageClaimedChunksRequest
import serverutils.net.MessageClaimedChunksUpdate

/** Ephemeral copy of the claim windows ServerUtilities sends to this client. */
object ServerUtilitiesClaims {
    private const val WINDOW = 15
    private const val REFRESH_NANOS = 15_000_000_000L
    private const val REQUEST_GAP_NANOS = 250_000_000L
    private const val MAX_PAGE_RADIUS = 4
    private val logger = logger<ServerUtilitiesClaims>()
    private val claims = HashMap<Pair<Int, Int>, ClaimMark>()
    private val requested = HashMap<Pair<Int, Int>, Long>()
    private var dimension: Int? = null
    private var lastRequest = 0L

    fun install() {
        MinecraftForge.EVENT_BUS.register(this)
        ClientEvents.disconnected.subscribe { clear() }
        logger.info("ServerUtilities claim layer ready")
    }

    fun marks(): List<ClaimMark> {
        checkDimension() ?: return emptyList()
        return claims.values.toList()
    }

    fun request(camera: MapCamera) {
        checkDimension() ?: return
        val now = System.nanoTime()
        if (now - lastRequest < REQUEST_GAP_NANOS) return
        val centerX = floor(camera.centerX / (16.0 * WINDOW)).toInt()
        val centerZ = floor(camera.centerZ / (16.0 * WINDOW)).toInt()
        claims.keys.removeIf { (x, z) ->
            kotlin.math.abs(Math.floorDiv(x, WINDOW) - centerX) > MAX_PAGE_RADIUS + 1 ||
                kotlin.math.abs(Math.floorDiv(z, WINDOW) - centerZ) > MAX_PAGE_RADIUS + 1
        }
        requested.keys.removeIf { (x, z) ->
            kotlin.math.abs(x - centerX) > MAX_PAGE_RADIUS + 1 ||
                kotlin.math.abs(z - centerZ) > MAX_PAGE_RADIUS + 1
        }
        val halfX = (camera.width / (2.0 * camera.pixelsPerBlock * 16 * WINDOW)).toInt() + 1
        val halfZ = (camera.height / (2.0 * camera.pixelsPerBlock * 16 * WINDOW)).toInt() + 1
        val radiusX = halfX.coerceAtMost(MAX_PAGE_RADIUS)
        val radiusZ = halfZ.coerceAtMost(MAX_PAGE_RADIUS)
        val next =
            (-radiusZ..radiusZ)
                .flatMap { dz ->
                    (-radiusX..radiusX).map { dx -> centerX + dx to centerZ + dz }
                }
                .sortedBy { (x, z) ->
                    val dx = x - centerX
                    val dz = z - centerZ
                    dx * dx + dz * dz
                }
                .firstOrNull { page -> requested[page]?.let { now - it >= REFRESH_NANOS } ?: true }
                ?: return
        lastRequest = now
        requested[next] = now
        MessageClaimedChunksRequest(next.first * WINDOW, next.second * WINDOW).sendToServer()
    }

    @SubscribeEvent
    fun onUpdate(event: UpdateClientDataEvent) {
        val message = event.message
        Minecraft.getMinecraft().func_152344_a { applyUpdate(message) }
    }

    private fun applyUpdate(message: MessageClaimedChunksUpdate) {
        checkDimension() ?: return
        for (z in message.startZ until message.startZ + WINDOW) {
            for (x in message.startX until message.startX + WINDOW) claims.remove(x to z)
        }
        for (team in message.teams.values) {
            val owner = team.nameComponent.unformattedText
            val color = team.color.color.rgb()
            for ((index, chunk) in team.chunks) {
                val x = message.startX + index % WINDOW
                val z = message.startZ + index / WINDOW
                claims[x to z] = ClaimMark(x, z, owner, color, chunk.isLoaded)
            }
        }
    }

    private fun checkDimension(): Int? {
        val current = Minecraft.getMinecraft().theWorld?.provider?.dimensionId
        if (dimension != current) {
            clear()
            dimension = current
        }
        return current
    }

    private fun clear() {
        claims.clear()
        requested.clear()
        dimension = null
        lastRequest = 0L
    }
}
