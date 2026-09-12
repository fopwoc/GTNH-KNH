package io.github.fopwoc.mods.hotspot.client.gui.ui.theme

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle

fun hotspotTitleTextStyle(): TextStyle = TextStyle(color = HotspotPalette.Foreground)

fun hotspotSectionTitleTextStyle(): TextStyle = TextStyle(color = HotspotPalette.Gold)

fun hotspotBodyTextStyle(
    wrap: Boolean = true,
    color: Color = HotspotPalette.Foreground,
): TextStyle = TextStyle(color = color, wrap = wrap)
