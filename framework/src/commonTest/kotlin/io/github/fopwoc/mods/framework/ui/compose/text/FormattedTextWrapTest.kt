package io.github.fopwoc.mods.framework.ui.compose.text

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattedTextWrapTest {
    /** Monospace width that ignores `§x` codes, like a Minecraft font would. */
    private fun width(text: String): Int = text.replace(Regex("§."), "").length

    private fun wrap(text: String, maxWidth: Int) = FormattedTextWrap.wrap(text, maxWidth, ::width)

    @Test
    fun breaksAtTheLastFittingSpaceAndDropsIt() {
        assertEquals(listOf("hello", "world"), wrap("hello world", 8))
        assertEquals(listOf("a b c", "d"), wrap("a b c d", 5))
    }

    @Test
    fun splitsWordsLongerThanALine() {
        assertEquals(listOf("abcd", "efgh", "ij"), wrap("abcdefghij", 4))
    }

    @Test
    fun keepsParagraphsAndShortLines() {
        assertEquals(listOf("one", "", "two"), wrap("one\n\ntwo", 10))
        assertEquals(listOf("short"), wrap("short", 10))
    }

    @Test
    fun carriesColourAndStylesToContinuationLines() {
        assertEquals(listOf("§6§lgold bold", "§6§lstays"), wrap("§6§lgold bold stays", 9))
        assertEquals(listOf("§cred §rplain", "text"), wrap("§cred §rplain text", 9))
    }

    @Test
    fun formattingCodesTakeNoWidthAndAreNeverSplit() {
        assertEquals(listOf("ab§ecd", "§eef"), wrap("ab§ecdef", 4))
    }
}
