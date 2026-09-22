package io.github.fopwoc.mods.hotspot.protocol

const val HOTSPOT_PROTOCOL_VERSION = 1
const val HOTSPOT_CHANNEL_NAME = "hotspot"

/** Longest profiling run a client may ask for; the server config can lower it further. */
const val MAX_DURATION_TICKS = 20 * 60

/**
 * Vanilla custom payloads are capped at 32 KiB in 1.7.10, so a snapshot is streamed as parts that
 * stay comfortably below that.
 */
const val MAX_PART_BYTES = 24 * 1024
internal const val MAX_CHUNKS_PER_PART = 2048
internal const val MAX_TILE_ENTITIES_PER_CHUNK = 512
internal const val MAX_NAMES_PER_PART = 4096
internal const val MAX_NAME_LENGTH = 96
