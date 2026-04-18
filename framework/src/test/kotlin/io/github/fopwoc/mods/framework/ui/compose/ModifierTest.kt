package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnFill
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnWeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnParentData
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentSize
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentWidth
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxParentData
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.ParentDataKey
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.parentDataOrNull
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowFill
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowWeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowParentData
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.withParentData
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModifierTest {
    private data class SampleParentData(val value: String)

    private object SampleParentDataKey : ParentDataKey<SampleParentData>

    @Test
    fun modifierRemainsValueLikeAfterInternalParentDataCleanup() {
        val first = Modifier()
            .padding(4.uu)
            .background(Color.rgb(red = 0x12, green = 0x34, blue = 0x56))
            .tooltip("Helpful")
            .offset(x = 1.uu, y = 2.uu)
        val second = Modifier()
            .padding(4.uu)
            .background(Color.rgb(red = 0x12, green = 0x34, blue = 0x56))
            .tooltip(listOf("Helpful"))
            .offset(x = 1.uu, y = 2.uu)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun tooltipModifierCanStoreMultipleLinesAndBeCleared() {
        val modifier = Modifier().tooltip(listOf("Title", "Body"))

        assertEquals(listOf("Title", "Body"), modifier.tooltipLines?.map { it.plainText })
        assertEquals(null, modifier.tooltip(emptyList()).tooltipLines)
        assertEquals(null, modifier.tooltip("").tooltipLines)
    }

    @Test
    fun tooltipModifierSupportsStyledTextLines() {
        val modifier = Modifier().tooltip(
            styledText {
                append("Name: ")
                withColor(MinecraftColor.Gold) {
                    append("Luna")
                }
            },
            styledText {
                withBold {
                    append("Online")
                }
            }
        )

        assertEquals(listOf("Name: Luna", "Online"), modifier.tooltipLines?.map { it.plainText })
        assertEquals(listOf("Name: §6Luna", "§lOnline"), modifier.tooltipLines?.map { it.formattedString })
    }

    @Test
    fun boxParentDataParticipatesInEqualityWithoutLeakingInToString() {
        val first = Modifier()
            .boxParentData(alignment = Alignment.Center)
            .boxParentData(matchParentWidth = true)
            .boxParentData(matchParentHeight = true)
        val second = Modifier()
            .boxParentData(matchParentSize = true)
            .boxParentData(alignment = Alignment.Center)

        assertEquals(first, second)
        assertEquals(Alignment.Center, first.boxAlignment)
        assertTrue(first.boxMatchesParentWidth)
        assertTrue(first.boxMatchesParentHeight)
        assertTrue(first.boxMatchesParentSize)
        assertFalse(first.toString().contains("boxParentData"))
    }

    @Test
    fun genericParentDataInfrastructureSupportsMultipleScopedLayoutsLater() {
        val modifier = Modifier()
            .withParentData(SampleParentDataKey, { SampleParentData("default") }) {
                it.copy(value = "future-row-scope")
            }
            .boxParentData(alignment = Alignment.BottomEnd)
            .rowParentData(alignment = VerticalAlignment.BOTTOM, weight = 2f, fill = false)
            .columnParentData(alignment = HorizontalAlignment.END, weight = 3f)

        assertEquals("future-row-scope", modifier.parentDataOrNull(SampleParentDataKey)?.value)
        assertEquals(Alignment.BottomEnd, modifier.boxAlignment)
        assertEquals(VerticalAlignment.BOTTOM, modifier.rowAlignment)
        assertEquals(2f, modifier.rowWeight)
        assertFalse(modifier.rowFill)
        assertEquals(HorizontalAlignment.END, modifier.columnAlignment)
        assertEquals(3f, modifier.columnWeight)
        assertTrue(modifier.columnFill)
    }

    @Test
    fun weightedParentDataRequiresPositiveWeight() {
        assertFailsWith<IllegalArgumentException> {
            Modifier().rowParentData(weight = 0f)
        }

        assertFailsWith<IllegalArgumentException> {
            Modifier().columnParentData(weight = -1f)
        }
    }

    @Test
    fun colorPreservesPackedArgbExactlyAndSupportsCopyingAlpha() {
        val packed = Color(0x00123456)
        val rgb = Color.rgb(red = 0x12, green = 0x34, blue = 0x56)
        val translucent = rgb.copy(alpha = 0x1A)

        assertEquals(0x00, packed.alpha)
        assertEquals(0x12, packed.red)
        assertEquals(0x34, packed.green)
        assertEquals(0x56, packed.blue)
        assertEquals(0xFF, rgb.alpha)
        assertEquals(Color(0xFF123456), rgb)
        assertEquals(0x1A, translucent.alpha)
        assertEquals(Color.argb(alpha = 0x1A, red = 0x12, green = 0x34, blue = 0x56), translucent)
    }
}

