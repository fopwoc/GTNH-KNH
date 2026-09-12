package io.github.fopwoc.mods.hotspot.client.profile

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.serialization.JsonFileStorage
import io.github.fopwoc.mods.hotspot.MOD_ID
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshot
import java.io.File
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager

/** Last snapshot and picks per world/server, under `config/hotspot/profiles/<context>.json`. */
@SideOnly(Side.CLIENT)
object ProfilePersistence {
  private val logger = LogManager.getLogger(ProfilePersistence::class.java)

  @Serializable
  data class Saved(
      val version: Int = 1,
      val snapshot: ProfileSnapshot? = null,
      val focusedChunk: ChunkRef? = null,
      val selectedTileEntities: List<TileEntityRef> = emptyList(),
  )

  fun load(contextId: String): Saved =
      JsonFileStorage.readOrDefault(file(contextId), defaultValue = ::Saved) {
        logger.warn("Failed to read saved profile for {}", contextId, it)
      }

  fun save(contextId: String, saved: Saved) {
    runCatching { JsonFileStorage.write(file(contextId), saved) }
        .onFailure { logger.warn("Failed to save profile for {}", contextId, it) }
  }

  private fun file(contextId: String): File =
      JsonFileStorage.modConfigFile(
          Minecraft.getMinecraft().mcDataDir,
          MOD_ID,
          "profiles",
          "$contextId.json",
      )
}
