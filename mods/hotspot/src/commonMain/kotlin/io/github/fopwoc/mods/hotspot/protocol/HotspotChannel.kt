package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.ModChannel

object HotspotChannel :
    ModChannel(HOTSPOT_CHANNEL_NAME, protocolVersion = HOTSPOT_PROTOCOL_VERSION) {
    val requests = serverbound(ProfileRequestCodec)
    val statuses = clientbound(ProfileStatusCodec)
    val parts = clientbound(ProfileSnapshotPartCodec)
    val accessChecks = serverbound(AccessCheckCodec)
    val accessReplies = clientbound(AccessReplyCodec)
}
