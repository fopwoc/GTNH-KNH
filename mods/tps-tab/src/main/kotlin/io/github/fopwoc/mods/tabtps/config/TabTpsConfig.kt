package io.github.fopwoc.mods.tabtps.config

import io.github.fopwoc.mods.framework.config.ForgeConfig
import io.github.fopwoc.mods.tabtps.MOD_ID

const val DEFAULT_PLACEHOLDER_TEXT = "Requesting server TPS..."

object TabTpsConfig : ForgeConfig(modId = MOD_ID, fileName = "tab_tps.cfg") {
  private const val MAX_UPDATE_INTERVAL_TICKS = 72_000

  val enabled by
      boolean(
          "enabled",
          default = true,
          comment = "Show the TPS card while the player list is open.",
      )

  val showServerMetrics by
      boolean("showServerMetrics", default = true, comment = "Show whole-server TPS and MSPT.")

  val showCurrentDimensionMetrics by
      boolean(
          "showCurrentDimensionMetrics",
          default = true,
          comment = "Show metrics for the dimension the player is currently in.",
      )

  private val dimensionIdsText by
      string(
          "dimensionIds",
          default = "",
          comment = "Comma-separated dimension IDs to keep in the card.",
          normalize = { DimensionIdList.format(DimensionIdList.parse(it)) },
      )

  val cardAlignment by
      enum(
          "cardAlignment",
          default = CardHorizontalAlignment.CENTER,
          comment = "Horizontal card alignment below the player list.",
      )

  val updateIntervalTicks by
      int(
          "updateIntervalTicks",
          default = 20,
          min = 1,
          max = MAX_UPDATE_INTERVAL_TICKS,
          comment = "Ticks between requests while Tab is held. 20 ticks is one second.",
      )

  val staleDataTicks by
      int(
          "staleDataTicks",
          default = 60,
          min = 1,
          comment = "Age in ticks after which the last response is marked stale.",
          normalize = { it.coerceAtLeast(updateIntervalTicks * 2) },
      )

  val showPlaceholder by
      boolean(
          "showPlaceholder",
          default = true,
          comment = "Show a status card while waiting for the first server response.",
      )

  val placeholderText by
      string(
          "placeholderText",
          default = DEFAULT_PLACEHOLDER_TEXT,
          comment = "Status text shown while waiting for the first server response.",
          normalize = { it.ifBlank { DEFAULT_PLACEHOLDER_TEXT } },
      )

  var dimensionIds: List<Int> = emptyList()
    private set

  override fun onLoaded() {
    dimensionIds = DimensionIdList.parse(dimensionIdsText)
  }

  val hasVisibleMetrics: Boolean
    get() = showServerMetrics || showCurrentDimensionMetrics || dimensionIds.isNotEmpty()

  fun requestedDimensionIds(currentDimensionId: Int): List<Int> =
      DimensionSelection.requested(
          currentDimensionId = currentDimensionId,
          includeCurrentDimension = showCurrentDimensionMetrics,
          pinnedDimensionIds = dimensionIds,
      )
}
