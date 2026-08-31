package io.github.fopwoc.mods.tabtps.config

import cpw.mods.fml.client.config.IConfigElement
import cpw.mods.fml.client.event.ConfigChangedEvent
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import io.github.fopwoc.mods.framework.serialization.FrameworkJson
import io.github.fopwoc.mods.tabtps.MOD_ID
import io.github.fopwoc.mods.tabtps.TabTpsMod
import java.io.File
import kotlinx.serialization.decodeFromString
import net.minecraftforge.common.config.ConfigElement
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.common.config.Property

const val DEFAULT_ENABLED = true
const val DEFAULT_SHOW_ALL_DIMENSIONS = false
const val DEFAULT_UPDATE_INTERVAL_TICKS = 20
const val DEFAULT_STALE_DATA_TICKS = 60
const val DEFAULT_SHOW_PLACEHOLDER = true
const val DEFAULT_PLACEHOLDER_TEXT = "Requesting server TPS..."

object TabTpsConfig {
  private const val CATEGORY = Configuration.CATEGORY_GENERAL
  private const val FILE_NAME = "tab_tps.cfg"
  private const val LEGACY_FILE_NAME = "tab_tps.json"
  private const val MAX_UPDATE_INTERVAL_TICKS = 72_000

  private var configuration: Configuration? = null
  private var configFile: File? = null
  private var lastModified = 0L

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
    val file = File(configDirectory, FILE_NAME)
    val legacy = if (!file.exists()) readLegacyConfig(configDirectory) else null

    configFile = file
    configuration = Configuration(file)
    synchronize(legacy)

    if (legacy != null) {
      TabTpsMod.logger.info("Imported legacy {} settings into {}", LEGACY_FILE_NAME, FILE_NAME)
    }
  }

  fun refreshIfChanged(): Boolean {
    val config = configuration ?: return false
    val file = configFile ?: return false
    val modified = file.lastModified()
    if (modified == lastModified) {
      return false
    }

    config.load()
    synchronize()
    return true
  }

  fun configElements(): List<IConfigElement<*>> {
    val config = checkNotNull(configuration) { "TPS Tab configuration has not been loaded" }
    configureProperties(config)
    return ConfigElement<Any>(config.getCategory(CATEGORY)).childElements
  }

  @SubscribeEvent
  fun onConfigChanged(event: ConfigChangedEvent.OnConfigChangedEvent) {
    if (event.modID == MOD_ID) {
      synchronize()
    }
  }

  private fun synchronize(seed: TabTpsConfigModel? = null) {
    val config = configuration ?: return
    val properties = configureProperties(config)
    seed?.let(properties::write)

    val normalized = normalize(properties.read())
    properties.write(normalized)
    if (config.hasChanged()) {
      config.save()
    }

    lastModified = configFile?.lastModified() ?: 0L
    apply(normalized)
  }

  private fun configureProperties(config: Configuration): ConfigProperties {
    config.setCategoryLanguageKey(CATEGORY, "config.tpstab.general")
    config.setCategoryPropertyOrder(CATEGORY, PROPERTY_ORDER)

    return ConfigProperties(
        enabled =
            config
                .get(
                    CATEGORY,
                    "enabled",
                    DEFAULT_ENABLED,
                    "Show the TPS card while the player list is open.",
                )
                .setLanguageKey("config.tpstab.enabled"),
        showAllDimensions =
            config
                .get(
                    CATEGORY,
                    "showAllDimensions",
                    DEFAULT_SHOW_ALL_DIMENSIONS,
                    "Request metrics for every loaded dimension instead of only the current one.",
                )
                .setLanguageKey("config.tpstab.showAllDimensions"),
        updateIntervalTicks =
            config
                .get(
                    CATEGORY,
                    "updateIntervalTicks",
                    DEFAULT_UPDATE_INTERVAL_TICKS,
                    "Ticks between requests while Tab is held. 20 ticks is one second.",
                    1,
                    MAX_UPDATE_INTERVAL_TICKS,
                )
                .setLanguageKey("config.tpstab.updateIntervalTicks"),
        staleDataTicks =
            config
                .get(
                    CATEGORY,
                    "staleDataTicks",
                    DEFAULT_STALE_DATA_TICKS,
                    "Age in ticks after which the last response is marked stale.",
                    1,
                    Int.MAX_VALUE,
                )
                .setLanguageKey("config.tpstab.staleDataTicks"),
        showPlaceholder =
            config
                .get(
                    CATEGORY,
                    "showPlaceholder",
                    DEFAULT_SHOW_PLACEHOLDER,
                    "Show a status card while waiting for the first server response.",
                )
                .setLanguageKey("config.tpstab.showPlaceholder"),
        placeholderText =
            config
                .get(
                    CATEGORY,
                    "placeholderText",
                    DEFAULT_PLACEHOLDER_TEXT,
                    "Status text shown while waiting for the first server response.",
                )
                .setLanguageKey("config.tpstab.placeholderText"),
    )
  }

  private fun readLegacyConfig(configDirectory: File): TabTpsConfigModel? {
    val file = File(configDirectory, LEGACY_FILE_NAME)
    if (!file.isFile) {
      return null
    }

    return runCatching {
          FrameworkJson.prettyConfig.decodeFromString<TabTpsConfigModel>(file.readText())
        }
        .onFailure { throwable ->
          TabTpsMod.logger.warn("Failed to import legacy {} configuration", file.name, throwable)
        }
        .getOrNull()
  }

  private fun normalize(config: TabTpsConfigModel): TabTpsConfigModel {
    val updateIntervalTicks = config.updateIntervalTicks.coerceIn(1, MAX_UPDATE_INTERVAL_TICKS)
    val staleDataFloor =
        (updateIntervalTicks.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return config.copy(
        updateIntervalTicks = updateIntervalTicks,
        staleDataTicks = config.staleDataTicks.coerceAtLeast(staleDataFloor),
        placeholderText =
            config.placeholderText.takeIf { it.isNotBlank() } ?: DEFAULT_PLACEHOLDER_TEXT,
    )
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

  private data class ConfigProperties(
      val enabled: Property,
      val showAllDimensions: Property,
      val updateIntervalTicks: Property,
      val staleDataTicks: Property,
      val showPlaceholder: Property,
      val placeholderText: Property,
  ) {
    fun read(): TabTpsConfigModel =
        TabTpsConfigModel(
            enabled = enabled.boolean,
            showAllDimensions = showAllDimensions.boolean,
            updateIntervalTicks = updateIntervalTicks.int,
            staleDataTicks = staleDataTicks.int,
            showPlaceholder = showPlaceholder.boolean,
            placeholderText = placeholderText.string,
        )

    fun write(config: TabTpsConfigModel) {
      enabled.set(config.enabled)
      showAllDimensions.set(config.showAllDimensions)
      updateIntervalTicks.set(config.updateIntervalTicks)
      staleDataTicks.set(config.staleDataTicks)
      showPlaceholder.set(config.showPlaceholder)
      placeholderText.set(config.placeholderText)
    }
  }

  private val PROPERTY_ORDER =
      listOf(
          "enabled",
          "showAllDimensions",
          "updateIntervalTicks",
          "staleDataTicks",
          "showPlaceholder",
          "placeholderText",
      )
}
