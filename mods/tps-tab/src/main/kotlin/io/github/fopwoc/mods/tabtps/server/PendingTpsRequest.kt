package io.github.fopwoc.mods.tabtps.server

data class PendingTpsRequest(
    val requestId: Long,
    val dimensionIds: List<Int>,
)
