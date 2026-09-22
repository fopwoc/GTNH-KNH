package io.github.fopwoc.mods.framework.config

import java.io.File
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.common.config.Property

/** One [ModConfig] stored as a Forge `.cfg` file with a single category. */
class ForgeConfigBinding(val config: ModConfig, private val file: File) {
    val configuration = Configuration(file)
    val category: String = Configuration.CATEGORY_GENERAL
    private var lastModified = 0L

    fun load() = synchronize()

    /** Reloads when the file was edited outside the game. Cheap enough to poll every second. */
    fun refreshIfChanged(): Boolean {
        if (file.lastModified() == lastModified) return false
        configuration.load()
        synchronize()
        return true
    }

    /** Declares every value on the category, in declaration order; the settings screen edits these. */
    fun bindAll(): List<Property> {
        configuration.setCategoryLanguageKey(category, config.categoryLanguageKey)
        configuration.setCategoryPropertyOrder(category, config.values.map(ConfigValue<*>::key))
        return config.values.map { value -> declare(value).setLanguageKey(value.languageKey) }
    }

    /** After an in-game edit, which Forge applied to the bound properties. */
    fun synchronize() {
        val properties = bindAll()
        config.values.zip(properties).forEach { (value, property) -> value.read(property) }
        config.completeLoad()
        config.values.zip(properties).forEach { (value, property) -> value.write(property) }
        if (configuration.hasChanged()) configuration.save()
        lastModified = file.lastModified()
    }

    private fun declare(value: ConfigValue<*>): Property =
        when (value) {
            is BooleanConfigValue -> configuration.get(category, value.key, value.default, value.comment)
            is IntConfigValue -> configuration.get(category, value.key, value.default, value.comment, value.min, value.max)
            is DoubleConfigValue -> configuration.get(category, value.key, value.default, value.comment, value.min, value.max)
            is StringConfigValue ->
                value.validValues?.let { configuration.get(category, value.key, value.default, value.comment, it.toTypedArray()) }
                    ?: configuration.get(category, value.key, value.default, value.comment)
            is EnumConfigValue<*> -> value.declareEnum()
        }

    private fun <E : Enum<E>> EnumConfigValue<E>.declareEnum(): Property =
        configuration.get(category, key, storedName(default), comment, entries.map(::storedName).toTypedArray())

    private fun ConfigValue<*>.read(property: Property) {
        when (this) {
            is BooleanConfigValue -> accept(property.boolean)
            is IntConfigValue -> accept(property.int)
            is DoubleConfigValue -> accept(property.double)
            is StringConfigValue -> accept(property.string)
            is EnumConfigValue<*> -> acceptEnum(property.string)
        }
    }

    private fun <E : Enum<E>> EnumConfigValue<E>.acceptEnum(stored: String) = accept(parse(stored))

    private fun ConfigValue<*>.write(property: Property) {
        when (this) {
            is BooleanConfigValue -> property.set(value)
            is IntConfigValue -> property.set(value)
            is DoubleConfigValue -> property.set(value)
            is StringConfigValue -> property.set(value)
            is EnumConfigValue<*> -> property.set(storedNameOfValue())
        }
    }

    private fun <E : Enum<E>> EnumConfigValue<E>.storedNameOfValue(): String = storedName(value)
}
