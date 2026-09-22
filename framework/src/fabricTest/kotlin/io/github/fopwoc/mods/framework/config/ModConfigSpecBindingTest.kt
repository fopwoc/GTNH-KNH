package io.github.fopwoc.mods.framework.config

import com.electronwill.nightconfig.core.CommentedConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.neoforged.neoforge.common.ModConfigSpec

class ModConfigSpecBindingTest {
    private class SampleConfig : ModConfig("sample", "sample") {
        val freeText by string("freeText", default = "", comment = "Free text")
        val limitedText by string("limitedText", default = "ok", comment = "Limited text", validValues = listOf("ok"))
    }

    @Test
    fun missingStringValuesAreCorrectedWithoutThrowing() {
        val spec = ModConfigSpecBinding(SampleConfig()).spec
        val loaded = CommentedConfig.inMemory()

        assertFalse(spec.isCorrect(loaded))
        spec.correct(loaded)
        assertTrue(spec.isCorrect(loaded))
        assertEquals("", loaded.get<String>("freeText"))
        assertEquals("ok", loaded.get<String>("limitedText"))
    }

    @Test
    fun settingUsesDeclaredTranslationKey() {
        val spec = ModConfigSpecBinding(SampleConfig()).spec
        val entry = spec.spec.get<ModConfigSpec.ValueSpec>("freeText")

        assertEquals("config.sample.freeText", entry.translationKey)
    }
}
