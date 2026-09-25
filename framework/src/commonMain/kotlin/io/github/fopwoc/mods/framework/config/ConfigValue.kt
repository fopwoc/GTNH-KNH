package io.github.fopwoc.mods.framework.config

import java.util.Locale
import kotlin.reflect.KProperty

/**
 * One typed setting of a [ModConfig]. Read it through property delegation; the value is the
 * normalized snapshot from the last load, never the live platform storage. The sealed subtypes tell
 * backends how to store and validate the setting.
 */
sealed class ConfigValue<T : Any>(
    val key: String,
    val default: T,
    val comment: String,
    val languageKey: String,
    private val normalize: (T) -> T,
) {
    @Volatile
    var value: T = default
        private set

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    /** Takes a value read from storage; normalization happens once the whole config is read. */
    fun accept(stored: T) {
        value = stored
    }

    internal fun applyNormalization() {
        value = normalize(value)
    }
}

class BooleanConfigValue
internal constructor(
    key: String,
    default: Boolean,
    comment: String,
    languageKey: String,
    normalize: (Boolean) -> Boolean,
) : ConfigValue<Boolean>(key, default, comment, languageKey, normalize)

class IntConfigValue
internal constructor(
    key: String,
    default: Int,
    comment: String,
    languageKey: String,
    val min: Int,
    val max: Int,
    /**
     * Text shown beside the field in settings screens that support it, for the value being edited.
     */
    val hint: ((Int) -> String)?,
    normalize: (Int) -> Int,
) : ConfigValue<Int>(key, default, comment, languageKey, { normalize(it.coerceIn(min, max)) })

class DoubleConfigValue
internal constructor(
    key: String,
    default: Double,
    comment: String,
    languageKey: String,
    val min: Double,
    val max: Double,
    normalize: (Double) -> Double,
) : ConfigValue<Double>(key, default, comment, languageKey, { normalize(it.coerceIn(min, max)) })

class StringConfigValue
internal constructor(
    key: String,
    default: String,
    comment: String,
    languageKey: String,
    /** Allowed values, or null for free text. */
    val validValues: List<String>?,
    normalize: (String) -> String,
) : ConfigValue<String>(key, default, comment, languageKey, normalize)

class EnumConfigValue<E : Enum<E>>
internal constructor(
    key: String,
    default: E,
    comment: String,
    languageKey: String,
    val entries: List<E>,
) : ConfigValue<E>(key, default, comment, languageKey, { it }) {

    /** Stored name of [entry]: its lower-case constant name. */
    fun storedName(entry: E): String = entry.name.lowercase(Locale.ROOT)

    /** Entry for a stored name, case-insensitively; unknown names fall back to [default]. */
    fun parse(stored: String): E =
        entries.firstOrNull { it.name.equals(stored, ignoreCase = true) } ?: default
}
