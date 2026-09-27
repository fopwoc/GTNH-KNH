package io.github.fopwoc.mods.framework.minecraft

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ItemIdTest {
    @Test
    fun acceptsLegacyModNamesAndRejectsIncompleteIds() {
        assertEquals("GregTech:machine.01", ItemId("GregTech:machine.01").value)
        assertFailsWith<IllegalArgumentException> { ItemId("minecraft:") }
        assertFailsWith<IllegalArgumentException> { ItemId("diamond") }
    }
}
