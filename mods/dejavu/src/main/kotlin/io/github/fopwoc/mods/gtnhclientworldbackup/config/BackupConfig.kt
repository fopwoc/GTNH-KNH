package io.github.fopwoc.mods.gtnhclientworldbackup.config

import io.github.fopwoc.mods.framework.config.ForgeConfig
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_ID

private const val DEFAULT_SAVE_NAME_PREFIX = "observed-"

object BackupConfig : ForgeConfig(modId = MOD_ID, fileName = "$MOD_ID.cfg") {
  val enabled by
      boolean("enabled", default = true, comment = "Archive chunks received from servers.")

  val autosaveIntervalSeconds by
      int(
          "autosaveIntervalSeconds",
          default = 15,
          min = 5,
          max = 300,
          comment = "Seconds between autosaves.",
      )

  val flushEverySavedChunks by
      int(
          "flushEverySavedChunks",
          default = 8,
          min = 1,
          max = 64,
          comment = "Flush after this many chunks.",
      )

  val maxChunkRadius by
      int(
          "maxChunkRadius",
          default = 0,
          min = 0,
          max = 32,
          comment = "0 keeps every received chunk.",
      )

  val saveSingleplayer by
      boolean("saveSingleplayer", default = false, comment = "Also archive singleplayer worlds.")

  val showHud by boolean("showHud", default = false, comment = "Show the backup status HUD.")

  val saveNamePrefix by
      string(
          "saveNamePrefix",
          default = DEFAULT_SAVE_NAME_PREFIX,
          comment = "Prefix for archived world folders.",
          normalize = { it.trim().ifBlank { DEFAULT_SAVE_NAME_PREFIX } },
      )

  val showChunkHighlights by
      boolean(
          "showChunkHighlights",
          default = true,
          comment = "Highlight archived chunks in world.",
      )

  val highlightOnlyTargetedChunk by
      boolean(
          "highlightOnlyTargetedChunk",
          default = false,
          comment = "Highlight only the targeted chunk.",
      )

  val highlightRenderRadiusChunks by
      int(
          "highlightRenderRadiusChunks",
          default = 12,
          min = 1,
          max = 64,
          comment = "Highlight radius in chunks.",
      )

  private val highlightFillAlphaValue by
      double(
          "highlightFillAlpha",
          default = 0.08,
          min = 0.0,
          max = 0.40,
          comment = "Chunk fill alpha.",
      )

  private val highlightOutlineAlphaValue by
      double(
          "highlightOutlineAlpha",
          default = 0.65,
          min = 0.05,
          max = 1.0,
          comment = "Chunk outline alpha.",
      )

  val highlightFillAlpha: Float
    get() = highlightFillAlphaValue.toFloat()

  val highlightOutlineAlpha: Float
    get() = highlightOutlineAlphaValue.toFloat()
}
