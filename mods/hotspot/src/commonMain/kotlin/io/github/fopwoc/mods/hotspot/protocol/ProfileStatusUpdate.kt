package io.github.fopwoc.mods.hotspot.protocol

/**
 * Server's answer to a [ProfileRequest]: whether it started, how long it still takes, and the
 * longest window this server allows so the client can stop asking for more.
 */
data class ProfileStatusUpdate(
    val requestId: Long,
    val status: ProfileStatus,
    val remainingTicks: Int,
    val maxDurationTicks: Int,
)
