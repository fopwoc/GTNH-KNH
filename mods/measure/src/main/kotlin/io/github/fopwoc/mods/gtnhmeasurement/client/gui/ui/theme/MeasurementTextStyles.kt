package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle

fun measurementTitleTextStyle(): TextStyle = TextStyle(color = MeasurementPalette.Foreground)

fun measurementSectionTitleTextStyle(): TextStyle = TextStyle(color = MeasurementPalette.Gold)

fun measurementBodyTextStyle(
    wrap: Boolean = false,
    color: Color = MeasurementPalette.Foreground,
    alignment: HorizontalAlignment = HorizontalAlignment.START,
): TextStyle =
    TextStyle(
        color = color,
        wrap = wrap,
        alignment = alignment,
    )
