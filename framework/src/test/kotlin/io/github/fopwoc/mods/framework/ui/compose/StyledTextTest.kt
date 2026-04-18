package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import kotlin.test.Test
import kotlin.test.assertEquals

class StyledTextTest {
    @Test
    fun styledTextBuilderProducesPlainTextAndFormattedString() {
        val text = styledText {
            append("Status: ")
            withColor(MinecraftColor.Green) {
                append("Online")
            }
            append(" · ")
            withItalic {
                append("5 players")
            }
        }

        assertEquals("Status: Online · 5 players", text.plainText)
        assertEquals("Status: §aOnline§r · §o5 players", text.formattedString)
    }

    @Test
    fun nestedScopesComposeIntoSingleSpanStyle() {
        val text = styledText {
            withColor(MinecraftColor.Gold) {
                append("A")
                withBold {
                    append("B")
                }
                append("C")
            }
        }

        assertEquals("§6A§r§6§lB§r§6C", text.formattedString)
    }

    @Test
    fun plainFactoryReturnsStableEmptyAndSimpleTextValues() {
        assertEquals(StyledText.Empty, StyledText.of(""))
        assertEquals("Hello", StyledText.of("Hello").plainText)
        assertEquals("Hello", StyledText.of("Hello").formattedString)
    }
}
