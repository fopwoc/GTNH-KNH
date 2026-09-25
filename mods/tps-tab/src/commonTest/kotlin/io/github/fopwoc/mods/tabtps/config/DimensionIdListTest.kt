package io.github.fopwoc.mods.tabtps.config

import kotlin.test.Test
import kotlin.test.assertEquals

class DimensionIdListTest {
    @Test
    fun parsesSignedIdsAndRemovesInvalidAndDuplicateEntries() {
        assertEquals(listOf("0", "-1", "7"), DimensionIdList.parse("0, -1, nope, 007, 7, 0"))
    }

    @Test
    fun formatsCanonicalConfigValue() {
        assertEquals("0, -1, 7", DimensionIdList.format(listOf("0", "-1", "7")))
    }

    @Test
    fun acceptsModernResourceKeysAndRejectsMalformedKeys() {
        assertEquals(
            listOf("minecraft:overworld", "mymod:mining", "-1"),
            DimensionIdList.parse(
                "minecraft:overworld, mymod:mining, BAD:Key, -1, minecraft:overworld"
            ),
        )
    }
}
