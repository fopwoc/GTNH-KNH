package io.github.fopwoc.mods.hotspot.protocol

/** Client → server: "may I profile here?", sent when the menu opens. */
data class AccessCheck(val nonce: Long)
