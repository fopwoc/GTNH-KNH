package io.github.fopwoc.mods.tabtps.protocol

data class TpsRequest(
    val requestId: Long,
    val dimensionIds: List<Int>,
)
