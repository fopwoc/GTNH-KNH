package io.github.fopwoc.mods.hotspot.client.profile

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.serialization.WorldScopedJsonStore
import io.github.fopwoc.mods.hotspot.MOD_ID
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshot
import kotlinx.serialization.Serializable

/** Last snapshot and picks per world/server, under `config/hotspot/profiles/<context>.json`. */
@SideOnly(Side.CLIENT)
object ProfilePersistence {
  @Serializable
  data class Saved(
      val version: Int = 1,
      val snapshot: ProfileSnapshot? = null,
      val focusedChunk: ChunkRef? = null,
      val selectedTileEntities: List<TileEntityRef> = emptyList(),
  )

  val store =
      WorldScopedJsonStore(
          modId = MOD_ID,
          directory = "profiles",
          serializer = Saved.serializer(),
          defaultValue = ::Saved,
      )
}
