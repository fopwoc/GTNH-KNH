package io.github.fopwoc.mods.hotspot.config

import io.github.fopwoc.mods.framework.config.ForgeConfig
import io.github.fopwoc.mods.hotspot.MOD_ID

/** Client-side overlay settings; editable in game under Mods → Hotspot → Config. */
object HotspotConfig : ForgeConfig(modId = MOD_ID, fileName = "hotspot.cfg") {
  val defaultDurationSeconds by
      int(
          "defaultDurationSeconds",
          default = 5,
          min = 1,
          max = 60,
          comment = "Profiling window preselected in the menu.",
      )

  val showLabels by
      boolean(
          "showLabels",
          default = true,
          comment = "Draw ms labels on highlighted chunks and blocks.",
      )

  val showTechnicalNames by
      boolean(
          "showTechnicalNames",
          default = true,
          comment = "Add the tile entity class name under the block label.",
      )

  val labelDistance by
      int(
          "labelDistance",
          default = 64,
          min = 8,
          max = 512,
          comment = "Blocks beyond which labels are hidden; boxes and columns stay.",
      )

  val chunkFillAlpha by
      double(
          "chunkFillAlpha",
          default = 0.10,
          min = 0.0,
          max = 1.0,
          comment = "Opacity of the highlighted chunk column.",
      )

  val chunkColumnHeight by
      int(
          "chunkColumnHeight",
          default = 256,
          min = 16,
          max = 256,
          comment = "Height of the chunk column from y=0.",
      )
}
