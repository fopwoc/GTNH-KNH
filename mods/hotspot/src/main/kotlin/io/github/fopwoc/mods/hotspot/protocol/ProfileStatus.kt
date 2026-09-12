package io.github.fopwoc.mods.hotspot.protocol

/** Server's answer to a [ProfileRequest]; wire order is stable, append only. */
enum class ProfileStatus {
  /** Profiling is running; the snapshot follows after `remainingTicks`. */
  STARTED,
  /** The player is not on the server's allow list. */
  DENIED,
  /** Opis / MobiusCore is not installed on the server, so there is nothing to read. */
  PROFILER_UNAVAILABLE,
}
