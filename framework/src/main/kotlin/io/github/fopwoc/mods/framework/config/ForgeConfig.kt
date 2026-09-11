package io.github.fopwoc.mods.framework.config

import cpw.mods.fml.client.event.ConfigChangedEvent
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import java.io.File
import java.util.Locale
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.common.config.Property
import org.apache.logging.log4j.LogManager

/**
 * Declarative wrapper over a Forge `.cfg` file with a single category.
 *
 * Subclasses declare settings with the `boolean`/`int`/`double`/`string`/`enum` builders and read
 * them as plain properties. Values are normalized after every load in declaration order, so a
 * normalizer may read settings declared before it. Call [load] in pre-init, register the object on
 * the FML bus so in-game edits (`ConfigChangedEvent`) are picked up, and poll [refreshIfChanged] to
 * notice external edits.
 *
 * ```kotlin
 * object MyConfig : ForgeConfig(modId = MOD_ID, fileName = "my_mod.cfg") {
 *   val enabled by boolean("enabled", default = true, comment = "Turn the thing on.")
 *   val interval by int("interval", default = 20, min = 1, max = 1200, comment = "Ticks.")
 * }
 * ```
 */
abstract class ForgeConfig(
    val modId: String,
    private val fileName: String,
    languageKeyPrefix: String = "config.$modId",
) {
  private val logger = LogManager.getLogger(javaClass)
  private val category = Configuration.CATEGORY_GENERAL
  private val categoryLanguageKey = "$languageKeyPrefix.general"
  private val values = mutableListOf<ConfigValue<*>>()
  private val keyPrefix = languageKeyPrefix

  private var configuration: Configuration? = null
  private var file: File? = null
  private var lastModified = 0L

  /** Increments whenever a load produced new values. */
  @Volatile
  var revision: Long = 0
    private set

  fun load(configDirectory: File) {
    val target = File(configDirectory, fileName)
    file = target
    configuration = Configuration(target)
    synchronize()
    logger.info("Loaded {} from {}", javaClass.simpleName, target.name)
  }

  /** Reloads when the file was edited outside the game. Cheap enough to poll every second. */
  fun refreshIfChanged(): Boolean {
    val config = configuration ?: return false
    val target = file ?: return false
    if (target.lastModified() == lastModified) {
      return false
    }

    config.load()
    synchronize()
    return true
  }

  @SubscribeEvent
  fun onConfigChanged(event: ConfigChangedEvent.OnConfigChangedEvent) {
    if (event.modID == modId) {
      synchronize()
    }
  }

  /** Called after every load with normalized values; for derived state and logging. */
  protected open fun onLoaded() = Unit

  internal fun boundConfiguration(): Configuration =
      checkNotNull(configuration) { "${javaClass.simpleName} has not been loaded" }

  internal fun categoryName(): String = category

  internal fun bindAll(): List<Property> {
    val config = boundConfiguration()
    config.setCategoryLanguageKey(category, categoryLanguageKey)
    config.setCategoryPropertyOrder(category, values.map(ConfigValue<*>::key))
    return values.map { it.bind(config, category) }
  }

  private fun synchronize() {
    val config = configuration ?: return
    val properties = bindAll()
    values.zip(properties).forEach { (value, property) -> value.load(property) }
    values.forEach { it.applyNormalization() }
    values.zip(properties).forEach { (value, property) -> value.store(property) }
    if (config.hasChanged()) {
      config.save()
    }
    lastModified = file?.lastModified() ?: 0L
    revision += 1
    onLoaded()
  }

  private fun <T : Any> register(value: ConfigValue<T>): ConfigValue<T> {
    require(values.none { it.key == value.key }) { "Duplicate config key ${value.key}" }
    values += value
    return value
  }

  protected fun boolean(
      key: String,
      default: Boolean,
      comment: String,
      normalize: (Boolean) -> Boolean = { it },
  ): ConfigValue<Boolean> =
      register(
          ConfigValue(
              key = key,
              default = default,
              comment = comment,
              languageKey = "$keyPrefix.$key",
              normalize = normalize,
              declare = { config, category -> config.get(category, key, default, comment) },
              read = Property::getBoolean,
              write = Property::set,
          )
      )

  protected fun int(
      key: String,
      default: Int,
      comment: String,
      min: Int = Int.MIN_VALUE,
      max: Int = Int.MAX_VALUE,
      normalize: (Int) -> Int = { it },
  ): ConfigValue<Int> =
      register(
          ConfigValue(
              key = key,
              default = default,
              comment = comment,
              languageKey = "$keyPrefix.$key",
              normalize = { normalize(it.coerceIn(min, max)) },
              declare = { config, category ->
                config.get(category, key, default, comment, min, max)
              },
              read = Property::getInt,
              write = Property::set,
          )
      )

  protected fun double(
      key: String,
      default: Double,
      comment: String,
      min: Double = -Double.MAX_VALUE,
      max: Double = Double.MAX_VALUE,
      normalize: (Double) -> Double = { it },
  ): ConfigValue<Double> =
      register(
          ConfigValue(
              key = key,
              default = default,
              comment = comment,
              languageKey = "$keyPrefix.$key",
              normalize = { normalize(it.coerceIn(min, max)) },
              declare = { config, category ->
                config.get(category, key, default, comment, min, max)
              },
              read = Property::getDouble,
              write = Property::set,
          )
      )

  protected fun string(
      key: String,
      default: String,
      comment: String,
      validValues: List<String>? = null,
      normalize: (String) -> String = { it },
  ): ConfigValue<String> =
      register(
          ConfigValue(
              key = key,
              default = default,
              comment = comment,
              languageKey = "$keyPrefix.$key",
              normalize = normalize,
              declare = { config, category ->
                if (validValues == null) {
                  config.get(category, key, default, comment)
                } else {
                  config.get(category, key, default, comment, validValues.toTypedArray())
                }
              },
              read = Property::getString,
              write = Property::set,
          )
      )

  /** Enum stored by lower-case name; unknown values fall back to [default]. */
  protected inline fun <reified E : Enum<E>> enum(
      key: String,
      default: E,
      comment: String,
  ): ConfigValue<E> = enum(key, default, comment, enumValues<E>().toList())

  protected fun <E : Enum<E>> enum(
      key: String,
      default: E,
      comment: String,
      entries: List<E>,
  ): ConfigValue<E> =
      register(
          ConfigValue(
              key = key,
              default = default,
              comment = comment,
              languageKey = "$keyPrefix.$key",
              normalize = { it },
              declare = { config, category ->
                config.get(
                    category,
                    key,
                    default.configName(),
                    comment,
                    entries.map { it.configName() }.toTypedArray(),
                )
              },
              read = { property ->
                entries.firstOrNull { it.name.equals(property.string, ignoreCase = true) }
                    ?: default
              },
              write = { property, value -> property.set(value.configName()) },
          )
      )

  private fun Enum<*>.configName(): String = name.lowercase(Locale.ROOT)
}
