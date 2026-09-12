package io.github.fopwoc.mods.hotspot.protocol

/** Server's answer to a [ProfileRequest]: whether it started and how long it still takes. */
data class ProfileStatusUpdate(
    val requestId: Long,
    val status: ProfileStatus,
    val remainingTicks: Int,
)
