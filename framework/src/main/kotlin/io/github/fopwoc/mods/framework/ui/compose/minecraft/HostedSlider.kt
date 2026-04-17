package io.github.fopwoc.mods.framework.ui.compose.minecraft

import cpw.mods.fml.client.config.GuiSlider

internal class HostedSlider(
    var widget: GuiSlider,
    var label: String,
    var suffix: String,
    var valueRangeStart: Double,
    var valueRangeEnd: Double,
    var showDecimal: Boolean,
    var onValueChange: (Double) -> Unit
) {
    var lastSeenEpoch: Int = -1
    var suppressCallback: Boolean = false
}

