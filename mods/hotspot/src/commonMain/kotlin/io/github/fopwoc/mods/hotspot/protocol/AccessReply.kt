package io.github.fopwoc.mods.hotspot.protocol

/** Server → client answer to an [AccessCheck]. */
data class AccessReply(
    val nonce: Long,
    val allowed: Boolean,
    val profilerAvailable: Boolean,
    val maxDurationTicks: Int,
)
