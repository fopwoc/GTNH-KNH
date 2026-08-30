package io.github.fopwoc.mods.tabtps.config

import io.github.fopwoc.mods.framework.config.JsonConfigLoader
import io.github.fopwoc.mods.framework.config.LiveJsonConfig
import io.github.fopwoc.mods.tabtps.TabTpsMod
import java.io.File

const val DEFAULT_ENABLED = true
const val DEFAULT_SHOW_ALL_DIMENSIONS = false
const val DEFAULT_UPDATE_INTERVAL_TICKS = 20
const val DEFAULT_STALE_DATA_TICKS = 60
const val DEFAULT_SHOW_PLACEHOLDER = true
const val DEFAULT_PLACEHOLDER_TEXT = "Requesting server TPS..."

object TabTpsConfig {
  private const val FILE_NAME = "tab_tps.json"

  private var liveConfig: LiveJsonConfig<TabTpsConfigModel>? = null

  var revision: Long = 0
    private set

  var enabled: Boolean = DEFAULT_ENABLED
    private set

  var showAllDimensions: Boolean = DEFAULT_SHOW_ALL_DIMENSIONS
    private set

  var updateIntervalTicks: Int = DEFAULT_UPDATE_INTERVAL_TICKS
    private set

  var staleDataTicks: Int = DEFAULT_STALE_DATA_TICKS
    private set

  var showPlaceholder: Boolean = DEFAULT_SHOW_PLACEHOLDER
    private set

  var placeholderText: String = DEFAULT_PLACEHOLDER_TEXT
    private set

  fun load(configDirectory: File) {
    liveConfig =
        JsonConfigLoader.live(
            configDirectory = configDirectory,
            fileName = FILE_NAME,
            defaultValue = ::TabTpsConfigModel,
            normalize = { config: TabTpsConfigModel ->
              val updateIntervalTicks = config.updateIntervalTicks.coerceAtLeast(1)
              val staleDataFloor =
                  (updateIntervalTicks.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
              config.copy(
                  updateIntervalTicks = updateIntervalTicks,
                  staleDataTicks = config.staleDataTicks.coerceAtLeast(staleDataFloor),
                  placeholderText =
                      config.placeholderText.takeIf { it.isNotBlank() } ?: DEFAULT_PLACEHOLDER_TEXT,
              )
            },
            onReadFailure = { file, throwable ->
              TabTpsMod.logger.warn(
                  "Failed to read {}, rewriting default config",
                  file.name,
                  throwable,
              )
            },
        )

    apply(liveConfig!!.load())
  }

  fun refreshIfChanged(): Boolean {
    val config = liveConfig ?: return false
    if (!config.refreshIfChanged()) {
      return false
    }

    apply(config.current())
    return true
  }

  private fun apply(config: TabTpsConfigModel) {
    enabled = config.enabled
    showAllDimensions = config.showAllDimensions
    updateIntervalTicks = config.updateIntervalTicks
    staleDataTicks = config.staleDataTicks
    showPlaceholder = config.showPlaceholder
    placeholderText = config.placeholderText
    revision += 1
  }
}
