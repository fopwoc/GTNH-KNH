package io.github.fopwoc.mods.framework.config

import net.neoforged.fml.config.ModConfig as LoaderModConfig
import net.neoforged.neoforge.common.ModConfigSpec

/**
 * One [ModConfig] as a NeoForge `ModConfigSpec`, which Fabric gets through Forge Config API Port.
 * The loader owns the TOML file, validation, file watching and the settings screen; [synchronize]
 * copies loaded values into the config and writes normalized values back.
 */
class ModConfigSpecBinding(val config: ModConfig) {
    private val entries: List<Entry<*>>
    val spec: ModConfigSpec
    val fileName: String = "${config.name}.toml"
    val type: LoaderModConfig.Type =
        when (config.scope) {
            ConfigScope.CLIENT -> LoaderModConfig.Type.CLIENT
            ConfigScope.COMMON -> LoaderModConfig.Type.COMMON
            ConfigScope.SERVER -> LoaderModConfig.Type.SERVER
        }

    init {
        val builder = ModConfigSpec.Builder()
        entries =
            config.values.map { value ->
                builder.comment(value.comment).translation(value.languageKey).entry(value)
            }
        spec = builder.build()
    }

    /** After the loader loaded or reloaded the file. */
    fun synchronize() {
        entries.forEach(Entry<*>::read)
        config.completeLoad()
        if (entries.map(Entry<*>::write).any { it }) spec.save()
    }

    private fun ModConfigSpec.Builder.entry(value: ConfigValue<*>): Entry<*> =
        when (value) {
            is BooleanConfigValue -> Entry(value, define(value.key, value.default), { it }, { it })
            is IntConfigValue -> Entry(value, defineInRange(value.key, value.default, value.min, value.max), { it }, { it })
            is DoubleConfigValue -> Entry(value, defineInRange(value.key, value.default, value.min, value.max), { it }, { it })
            is StringConfigValue -> {
                val valid = value.validValues
                Entry(
                    value,
                    define(value.key, value.default) { candidate: Any? ->
                        candidate is String && (valid == null || candidate in valid)
                    },
                    { it },
                    { it },
                )
            }
            is EnumConfigValue<*> -> enumEntry(value)
        }

    private fun <E : Enum<E>> ModConfigSpec.Builder.enumEntry(value: EnumConfigValue<E>): Entry<E> =
        Entry(value, defineEnum(value.key, value.default), { it }, { it })

    /** A setting and its spec value; [toStored]/[fromStored] convert where the representations differ. */
    private class Entry<T : Any>(
        private val value: ConfigValue<T>,
        private val stored: ModConfigSpec.ConfigValue<T>,
        private val fromStored: (T) -> T,
        private val toStored: (T) -> T,
    ) {
        fun read() = value.accept(fromStored(stored.get()))

        /** Writes the normalized value; true when storage changed. */
        fun write(): Boolean {
            val normalized = toStored(value.value)
            if (stored.get() == normalized) return false
            stored.set(normalized)
            return true
        }
    }
}
