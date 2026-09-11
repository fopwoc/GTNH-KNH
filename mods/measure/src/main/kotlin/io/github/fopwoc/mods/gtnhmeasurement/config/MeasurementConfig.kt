package io.github.fopwoc.mods.gtnhmeasurement.config

import io.github.fopwoc.mods.framework.config.ForgeConfig
import io.github.fopwoc.mods.gtnhmeasurement.MOD_ID
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementPlatformProfile

enum class ShortcutScheme {
  AUTO,
  MAC,
  STANDARD,
}

object MeasurementConfig : ForgeConfig(modId = MOD_ID, fileName = "measure.cfg") {
  val showShortcutHud by
      boolean(
          "showShortcutHud",
          default = true,
          comment = "Show the shortcut hint box above the hotbar while measuring.",
      )

  val shortcutScheme by
      enum(
          "shortcutScheme",
          default = ShortcutScheme.AUTO,
          comment =
              "Modifier keys and labels: auto-detect, macOS (Cmd/Option) or standard (Ctrl/Alt).",
      )

  val undoHistorySize by
      int(
          "undoHistorySize",
          default = 100,
          min = 1,
          max = 1000,
          comment = "Number of editor steps kept for undo.",
      )

  val hudMargin by
      int("hudMargin", default = 6, min = 0, max = 64, comment = "Gap between hotbar and hint box.")

  fun resolvePlatformProfile(osName: String?): MeasurementPlatformProfile =
      when (shortcutScheme) {
        ShortcutScheme.MAC -> MeasurementPlatformProfile.MAC
        ShortcutScheme.STANDARD -> MeasurementPlatformProfile.STANDARD
        ShortcutScheme.AUTO ->
            if (osName?.lowercase()?.contains("mac") == true) MeasurementPlatformProfile.MAC
            else MeasurementPlatformProfile.STANDARD
      }
}
