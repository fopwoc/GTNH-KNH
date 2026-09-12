package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.ModChannel

object HotspotChannel : ModChannel(HOTSPOT_CHANNEL_NAME) {
  val requests = serverbound(ProfileRequestMessage::class.java)
  val statuses = clientbound(ProfileStatusMessage::class.java)
  val parts = clientbound(ProfileSnapshotPartMessage::class.java)
}
