package io.github.fopwoc.mods.framework.ui.compose.component

import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

object SegmentedControlDefaults {
    val Spacing: UiUnit = UiTokens.SmallGap
    val ButtonWidth: UiUnit = UiTokens.StandardButtonWidth
    val SelectedColor: MinecraftColor = MinecraftColor.Yellow

    /**
     * Selected segments keep their button enabled and stand out by label style instead of greying.
     */
    fun selectedLabel(label: String, color: MinecraftColor = SelectedColor): StyledText =
        styledText {
            withColor(color) {
                withBold {
                    +label
                }
            }
        }
}
