package io.github.fopwoc.mods.tabtps.protocol

import io.github.fopwoc.mods.framework.network.ModChannel

object TpsChannel : ModChannel(TPS_CHANNEL_NAME) {
  val requests = serverbound(TpsRequestMessage::class.java)
  val snapshots = clientbound(TpsSnapshotMessage::class.java)
}
