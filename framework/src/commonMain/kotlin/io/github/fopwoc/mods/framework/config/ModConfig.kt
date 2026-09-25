package io.github.fopwoc.mods.framework.config

/**
 * Declarative mod settings shared by every platform.
 *
 * Subclasses declare settings with the `boolean`/`int`/`double`/`string`/`enum` builders and read
 * them as plain properties. Call [register] once from the mod's initialization; the platform stores
 * the settings in its native format (Forge `.cfg` on GTNH, `ModConfigSpec` TOML on modern loaders),
 * shows its native settings screen and reloads edits. Values are normalized after every load in
 * declaration order, so a normalizer may read settings declared before it.
 *
 * ```kotlin
 * object MyConfig : ModConfig(modId = MOD_ID, name = "my_mod") {
 *   val enabled by boolean("enabled", default = true, comment = "Turn the thing on.")
 *   val interval by int("interval", default = 20, min = 1, max = 1200, comment = "Ticks.")
 * }
 * ```
 */
abstract class ModConfig(
    val modId: String,
    /** File name without extension; each backend adds its own. */
    val name: String,
    val scope: ConfigScope = ConfigScope.CLIENT,
    languageKeyPrefix: String = "config.$modId",
) {
    private val keyPrefix = languageKeyPrefix
    private val declared = mutableListOf<ConfigValue<*>>()

    val categoryLanguageKey: String = "$languageKeyPrefix.general"

    /** Settings in declaration order. */
    val values: List<ConfigValue<*>>
        get() = declared

    /** Increments whenever a load produced new values. */
    @Volatile
    var revision: Long = 0
        private set

    fun register() = ConfigBackend.current.register(this)

    /**
     * Completes a load after the backend [accepted][ConfigValue.accept] every stored value:
     * normalizes in declaration order and notifies the config. Backends then write the normalized
     * values back to storage.
     */
    fun completeLoad() {
        declared.forEach(ConfigValue<*>::applyNormalization)
        revision += 1
        onLoaded()
    }

    /** Called after every load with normalized values; for derived state and logging. */
    protected open fun onLoaded() = Unit

    private fun <V : ConfigValue<*>> declare(value: V): V {
        require(declared.none { it.key == value.key }) { "Duplicate config key ${value.key}" }
        declared += value
        return value
    }

    private fun languageKey(key: String) = "$keyPrefix.$key"

    protected fun boolean(
        key: String,
        default: Boolean,
        comment: String,
        normalize: (Boolean) -> Boolean = { it },
    ): ConfigValue<Boolean> =
        declare(BooleanConfigValue(key, default, comment, languageKey(key), normalize))

    protected fun int(
        key: String,
        default: Int,
        comment: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
        normalize: (Int) -> Int = { it },
        hint: ((Int) -> String)? = null,
    ): ConfigValue<Int> =
        declare(IntConfigValue(key, default, comment, languageKey(key), min, max, hint, normalize))

    protected fun double(
        key: String,
        default: Double,
        comment: String,
        min: Double = -Double.MAX_VALUE,
        max: Double = Double.MAX_VALUE,
        normalize: (Double) -> Double = { it },
    ): ConfigValue<Double> =
        declare(DoubleConfigValue(key, default, comment, languageKey(key), min, max, normalize))

    protected fun string(
        key: String,
        default: String,
        comment: String,
        validValues: List<String>? = null,
        normalize: (String) -> String = { it },
    ): ConfigValue<String> =
        declare(StringConfigValue(key, default, comment, languageKey(key), validValues, normalize))

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
    ): ConfigValue<E> = declare(EnumConfigValue(key, default, comment, languageKey(key), entries))
}
