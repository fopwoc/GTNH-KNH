package io.github.fopwoc.mods.framework.config

import java.util.Locale
import java.util.function.Consumer
import java.util.function.Supplier
import net.minecraft.client.OptionInstance
import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.neoforged.fml.config.ModConfig as LoaderModConfig
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.common.ModConfigSpec

/** Keeps enum storage unchanged while translating choices in NeoForge's config screen. */
internal class LocalizedEnumConfigSection(
    parent: Screen,
    type: LoaderModConfig.Type,
    config: LoaderModConfig,
    title: Component,
) : ConfigurationScreen.ConfigurationSectionScreen(parent, type, config, title) {
    override fun <T : Enum<T>> createEnumValue(
        key: String,
        spec: ModConfigSpec.ValueSpec,
        source: Supplier<T>,
        target: Consumer<T>,
    ): Element {
        @Suppress("UNCHECKED_CAST")
        val choices = (spec.clazz as Class<T>).enumConstants.filter(spec::test)
        val option =
            OptionInstance(
                getTranslationKey(key),
                getTooltip(key, null),
                OptionInstance.CaptionBasedToString<T> { _, value ->
                    val translation =
                        "${getTranslationKey(key)}.${value.name.lowercase(Locale.ROOT)}"
                    if (Language.getInstance().has(translation)) Component.translatable(translation)
                    else Component.literal(value.name)
                },
                Custom(choices),
                source.get(),
                { selected: T ->
                    undoManager.add(
                        Consumer<T> { previous ->
                            target.accept(previous)
                            onChanged(key)
                        },
                        selected,
                        Consumer<T> { next ->
                            target.accept(next)
                            onChanged(key)
                        },
                        source.get(),
                    )
                },
            )
        return Element(getTranslationComponent(key), getTooltipComponent(key, null), option)
    }
}
