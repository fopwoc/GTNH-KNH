package io.github.fopwoc.mods.gtnhclientworldbackup.client.highlight

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.ClientWorldBackupManager
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.model.ChunkHighlightState
import io.github.fopwoc.mods.gtnhclientworldbackup.config.BackupConfig
import kotlin.math.abs
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MovingObjectPosition
import net.minecraftforge.client.event.RenderWorldLastEvent
import org.lwjgl.opengl.GL11

@SideOnly(Side.CLIENT)
object BackedUpChunkHighlighter {
    private const val CHUNK_SIZE = 16
    private const val WORLD_MIN_Y = 0.0
    private const val WORLD_MAX_Y = 256.0
    private const val BOX_INSET = 0.025
    private val SAVED_EARLIER_COLOR = Color.rgb(red = 0x49, green = 0xBE, blue = 0xFF)
    private val SAVED_THIS_SESSION_COLOR = Color.rgb(red = 0x51, green = 0xFF, blue = 0x79)

    private var highlightsEnabled = true
    private var observedConfigRevision = -1L

    fun initialize() {
        observedConfigRevision = -1L
        syncWithConfigIfNeeded()
    }

    fun areHighlightsEnabled(): Boolean {
        syncWithConfigIfNeeded()
        return highlightsEnabled
    }

    fun toggleHighlights(): Boolean {
        highlightsEnabled = !highlightsEnabled
        return highlightsEnabled
    }

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        syncWithConfigIfNeeded()
        if (!highlightsEnabled) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return
        val player = minecraft.thePlayer ?: return
        val savedChunks = ClientWorldBackupManager.getChunkHighlightSnapshot(world.provider.dimensionId)

        if (savedChunks.isEmpty()) {
            return
        }

        val interactionChunkKey = resolveInteractionChunkKey(minecraft)
        val nearbyChunks = savedChunks.filterKeys { chunkKey ->
            if (BackupConfig.highlightOnlyTargetedChunk) {
                chunkKey == interactionChunkKey
            } else {
                isChunkWithinRadius(chunkKey, player.chunkCoordX, player.chunkCoordZ, BackupConfig.highlightRenderRadiusChunks)
            }
        }

        if (nearbyChunks.isEmpty()) {
            return
        }

        val cameraEntity = minecraft.renderViewEntity ?: player
        val cameraX = interpolate(cameraEntity.lastTickPosX, cameraEntity.posX, event.partialTicks)
        val cameraY = interpolate(cameraEntity.lastTickPosY, cameraEntity.posY, event.partialTicks)
        val cameraZ = interpolate(cameraEntity.lastTickPosZ, cameraEntity.posZ, event.partialTicks)

        GL11.glPushMatrix()
        GL11.glDepthMask(false)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)

        try {
            nearbyChunks.forEach { (chunkKey, state) ->
                val isTargeted = chunkKey == interactionChunkKey
                renderChunkHighlight(chunkKey, state, cameraX, cameraY, cameraZ, isTargeted)
            }
        } finally {
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL11.GL_LIGHTING)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glDepthMask(true)
            GL11.glPopMatrix()
        }
    }

    private fun renderChunkHighlight(
        chunkKey: Long,
        state: ChunkHighlightState,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        targeted: Boolean
    ) {
        val chunkX = chunkXFromKey(chunkKey)
        val chunkZ = chunkZFromKey(chunkKey)
        val box = AxisAlignedBB.getBoundingBox(
            chunkX * CHUNK_SIZE + BOX_INSET - cameraX,
            WORLD_MIN_Y + BOX_INSET - cameraY,
            chunkZ * CHUNK_SIZE + BOX_INSET - cameraZ,
            (chunkX + 1) * CHUNK_SIZE - BOX_INSET - cameraX,
            WORLD_MAX_Y - BOX_INSET - cameraY,
            (chunkZ + 1) * CHUNK_SIZE - BOX_INSET - cameraZ
        )

        val color = when (state) {
            ChunkHighlightState.SAVED_EARLIER -> SAVED_EARLIER_COLOR
            ChunkHighlightState.SAVED_THIS_SESSION -> SAVED_THIS_SESSION_COLOR
        }
        val fillAlpha = (if (targeted) BackupConfig.highlightFillAlpha * 1.75f else BackupConfig.highlightFillAlpha)
            .coerceAtMost(0.5f)
        val outlineAlpha = (if (targeted) BackupConfig.highlightOutlineAlpha else BackupConfig.highlightOutlineAlpha * 0.85f)
            .coerceIn(0.05f, 1.0f)

        drawFilledBoundingBox(box, color, fillAlpha)
        GL11.glLineWidth(if (targeted) 3.0f else 1.5f)
        drawOutlinedBoundingBox(box, color, outlineAlpha)
    }

    private fun drawFilledBoundingBox(box: AxisAlignedBB, color: Color, alpha: Float) {
        val tessellator = Tessellator.instance
        tessellator.startDrawingQuads()
        tessellator.setColorRGBA_I(color.rgbInt, (alpha * 255.0f).toInt())

        // Top face
        tessellator.addVertex(box.minX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)

        // North face
        tessellator.addVertex(box.minX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)
        tessellator.addVertex(box.minX, box.maxY, box.minZ)

        // South face
        tessellator.addVertex(box.minX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.maxX, box.minY, box.maxZ)

        // West face
        tessellator.addVertex(box.minX, box.minY, box.minZ)
        tessellator.addVertex(box.minX, box.maxY, box.minZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.maxZ)

        // East face
        tessellator.addVertex(box.maxX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.maxZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)

        tessellator.draw()
    }

    private fun drawOutlinedBoundingBox(box: AxisAlignedBB, color: Color, alpha: Float) {
        val tessellator = Tessellator.instance
        val alphaInt = (alpha * 255.0f).toInt()

        tessellator.startDrawing(GL11.GL_LINE_STRIP)
        tessellator.setColorRGBA_I(color.rgbInt, alphaInt)
        tessellator.addVertex(box.minX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.minZ)
        tessellator.draw()

        tessellator.startDrawing(GL11.GL_LINE_STRIP)
        tessellator.setColorRGBA_I(color.rgbInt, alphaInt)
        tessellator.addVertex(box.minX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.minZ)
        tessellator.draw()

        tessellator.startDrawing(GL11.GL_LINES)
        tessellator.setColorRGBA_I(color.rgbInt, alphaInt)
        tessellator.addVertex(box.minX, box.minY, box.minZ)
        tessellator.addVertex(box.minX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.minZ)
        tessellator.addVertex(box.maxX, box.maxY, box.minZ)
        tessellator.addVertex(box.maxX, box.minY, box.maxZ)
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ)
        tessellator.addVertex(box.minX, box.minY, box.maxZ)
        tessellator.addVertex(box.minX, box.maxY, box.maxZ)
        tessellator.draw()
    }

    private fun resolveInteractionChunkKey(minecraft: Minecraft): Long? {
        val hit = minecraft.objectMouseOver
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return toChunkKey(hit.blockX shr 4, hit.blockZ shr 4)
        }

        val player = minecraft.thePlayer ?: return null
        return toChunkKey(player.chunkCoordX, player.chunkCoordZ)
    }

    private fun isChunkWithinRadius(chunkKey: Long, centerChunkX: Int, centerChunkZ: Int, radius: Int): Boolean {
        val chunkX = chunkXFromKey(chunkKey)
        val chunkZ = chunkZFromKey(chunkKey)
        return abs(chunkX - centerChunkX) <= radius && abs(chunkZ - centerChunkZ) <= radius
    }

    private fun interpolate(previous: Double, current: Double, partialTicks: Float): Double {
        return previous + (current - previous) * partialTicks
    }

    private fun syncWithConfigIfNeeded() {
        if (observedConfigRevision == BackupConfig.revision) {
            return
        }

        highlightsEnabled = BackupConfig.showChunkHighlights
        observedConfigRevision = BackupConfig.revision
    }

    private fun toChunkKey(chunkX: Int, chunkZ: Int): Long {
        return (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xFFFFFFFFL)
    }

    private fun chunkXFromKey(chunkKey: Long): Int = (chunkKey shr 32).toInt()

    private fun chunkZFromKey(chunkKey: Long): Int = chunkKey.toInt()
}


