package io.github.fopwoc.mods.hotspot.config

import io.github.fopwoc.mods.framework.config.ForgeConfig
import io.github.fopwoc.mods.hotspot.MOD_ID

/**
 * Server-side policy. Lives in its own file so a universal jar can carry both this and the client
 * overlay settings without mixing them; the file is loaded on the integrated server too.
 */
object HotspotServerConfig : ForgeConfig(modId = MOD_ID, fileName = "hotspot-server.cfg") {
  val allowedPlayers by
      string(
          "allowedPlayers",
          default = "",
          comment =
              "Comma-separated player names (or UUIDs) allowed to run the profiler. Op status is not required and not checked.",
      )

  val allowEveryone by
      boolean(
          "allowEveryone",
          default = false,
          comment = "Let every player profile. The singleplayer host is always allowed.",
      )

  val maxDurationSeconds by
      int(
          "maxDurationSeconds",
          default = 15,
          min = 1,
          max = 60,
          comment = "Longest profiling run a client may request.",
      )

  val minMicrosPerTileEntity by
      int(
          "minMicrosPerTileEntity",
          default = 5,
          min = 0,
          max = 1_000_000,
          comment =
              "Tile entities cheaper than this (microseconds per tick) are counted in chunk totals but not listed individually.",
      )

  val maxListedTileEntitiesPerChunk by
      int(
          "maxListedTileEntitiesPerChunk",
          default = 128,
          min = 1,
          max = 512,
          comment = "Heaviest tile entities sent per chunk.",
      )

  val allowedPlayerKeys: Set<String>
    get() =
        allowedPlayers
            .split(',')
            .map { it.trim().lowercase() }
            .filterTo(HashSet()) { it.isNotEmpty() }
}
