package io.github.fopwoc.mods.tabtps.monitor

import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot

data class TimedTpsSnapshot(
    val snapshot: TpsSnapshot,
    val receivedAtTick: Long,
)
