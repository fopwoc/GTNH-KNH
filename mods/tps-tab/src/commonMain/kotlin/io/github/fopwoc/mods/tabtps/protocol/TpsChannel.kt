package io.github.fopwoc.mods.tabtps.protocol

import io.github.fopwoc.mods.framework.network.ModChannel

object TpsChannel : ModChannel(TPS_CHANNEL_NAME, protocolVersion = TPS_PROTOCOL_VERSION) {
    val requests = serverbound(TpsRequestCodec)
    val snapshots = clientbound(TpsSnapshotCodec)
}
