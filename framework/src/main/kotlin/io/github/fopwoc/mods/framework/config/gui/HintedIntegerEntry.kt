package io.github.fopwoc.mods.framework.config.gui

import cpw.mods.fml.client.config.GuiConfig
import cpw.mods.fml.client.config.GuiConfigEntries
import cpw.mods.fml.client.config.IConfigElement
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.renderer.Tessellator
import net.minecraft.util.EnumChatFormatting

/**
 * An integer field that shows the setting's hint for the value being typed, right of the field and
 * as the last tooltip line, so the cost of a choice is visible before it is saved.
 */
@SideOnly(Side.CLIENT)
@Suppress("unused") // Forge instantiates it by class.
class HintedIntegerEntry(
    owningScreen: GuiConfig,
    owningEntryList: GuiConfigEntries,
    configElement: IConfigElement<*>,
) : GuiConfigEntries.IntegerEntry(owningScreen, owningEntryList, configElement) {
    private val baseToolTip: List<*> = toolTip.toList()

    private fun currentHint(): String? {
        val value = textFieldValue.text.trim().toIntOrNull() ?: return null
        return ConfigHints.hintFor(configElement.languageKey, value)
    }

    @Suppress("LongParameterList")
    override fun drawEntry(
        slotIndex: Int,
        x: Int,
        y: Int,
        listWidth: Int,
        slotHeight: Int,
        tessellator: Tessellator,
        mouseX: Int,
        mouseY: Int,
        isSelected: Boolean,
    ) {
        val hint = currentHint()
        val font = mc.fontRenderer
        val reserved =
            if (hint == null) 0
            else
                (font.getStringWidth(hint) + HINT_GAP).coerceAtMost(
                    owningEntryList.controlWidth / 2
                )
        // The field takes the whole control width from the shared list; narrow it for this row
        // only.
        val fullWidth = owningEntryList.controlWidth
        owningEntryList.controlWidth = fullWidth - reserved
        try {
            super.drawEntry(
                slotIndex,
                x,
                y,
                listWidth,
                slotHeight,
                tessellator,
                mouseX,
                mouseY,
                isSelected,
            )
        } finally {
            owningEntryList.controlWidth = fullWidth
        }
        if (hint != null && reserved > 0) {
            font.drawString(
                hint,
                owningEntryList.controlX + fullWidth - reserved + HINT_GAP,
                y + slotHeight / 2 - font.FONT_HEIGHT / 2,
                HINT_COLOR,
            )
        }
        toolTip =
            when (hint) {
                null -> baseToolTip
                else ->
                    baseToolTip +
                        font.listFormattedStringToWidth(
                            EnumChatFormatting.GRAY.toString() + hint,
                            TOOLTIP_WIDTH,
                        )
            }
    }

    private companion object {
        const val HINT_GAP = 6
        const val HINT_COLOR = 0xA0A0A0
        const val TOOLTIP_WIDTH = 300
    }
}
