package io.github.fopwoc.mods.hotspot.client.profile

import kotlinx.serialization.Serializable

@Serializable data class ChunkRef(val dimensionId: Int, val chunkX: Int, val chunkZ: Int)
