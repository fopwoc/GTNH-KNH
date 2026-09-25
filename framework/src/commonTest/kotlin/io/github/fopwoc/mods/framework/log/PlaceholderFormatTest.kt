package io.github.fopwoc.mods.framework.log

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderFormatTest {
    @Test
    fun substitutesPlaceholdersInOrder() {
        assertEquals(
            "KNH Core 1.2 ready",
            formatPlaceholders("{} {} ready", listOf("KNH Core", 1.2)),
        )
    }

    @Test
    fun keepsSurplusPlaceholdersAndIgnoresSurplusArguments() {
        assertEquals("a {} b", formatPlaceholders("{} {} b", listOf("a")))
        assertEquals("only a", formatPlaceholders("only {}", listOf("a", "b")))
    }

    @Test
    fun rendersNullArguments() {
        assertEquals("value null", formatPlaceholders("value {}", listOf(null)))
    }
}
