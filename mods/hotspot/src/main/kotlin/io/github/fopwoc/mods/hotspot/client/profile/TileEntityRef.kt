package io.github.fopwoc.mods.hotspot.client.profile

data class TileEntityRef(val dimensionId: Int, val x: Int, val y: Int, val z: Int) {
  val chunk: ChunkRef
    get() = ChunkRef(dimensionId, x shr 4, z shr 4)
}
