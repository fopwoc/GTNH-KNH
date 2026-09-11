package io.github.fopwoc.mods.framework.config

import kotlin.reflect.KProperty
import net.minecraftforge.common.config.Property

/**
 * One typed setting of a [ForgeConfig]. Read it through property delegation; the value is the
 * normalized snapshot from the last load, never the live Forge [Property].
 */
class ConfigValue<T : Any>
internal constructor(
    val key: String,
    val default: T,
    internal val comment: String,
    internal val languageKey: String,
    private val normalize: (T) -> T,
    private val declare: (net.minecraftforge.common.config.Configuration, String) -> Property,
    private val read: (Property) -> T,
    private val write: (Property, T) -> Unit,
) {
  @Volatile
  var value: T = default
    internal set

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

  internal fun bind(
      configuration: net.minecraftforge.common.config.Configuration,
      category: String,
  ): Property = declare(configuration, category).setLanguageKey(languageKey)

  internal fun load(property: Property) {
    value = read(property)
  }

  internal fun applyNormalization(): T {
    value = normalize(value)
    return value
  }

  internal fun store(property: Property) {
    write(property, value)
  }
}
