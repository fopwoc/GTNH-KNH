package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

interface TextMetrics {
    val lineHeight: Int

    fun textWidth(text: String): Int

    fun wrapText(text: String, maxWidth: Int): List<String>
}

interface RenderContext : TextMetrics {
    val viewportWidth: Int
    val viewportHeight: Int
    val mouseX: Int
    val mouseY: Int

    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color)

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color)

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color)

    fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean)

    fun drawVanillaButton(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        text: String,
        enabled: Boolean,
        onClick: () -> Unit
    )

    fun drawVanillaCheckbox(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        label: String,
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit
    )

    fun drawVanillaTextField(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        state: TextFieldState,
        placeholder: String,
        enabled: Boolean,
        style: TextFieldStyle
    )

    fun drawVanillaSlider(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        value: Double,
        valueRangeStart: Double,
        valueRangeEnd: Double,
        label: String,
        suffix: String,
        enabled: Boolean,
        showDecimal: Boolean,
        onValueChange: (Double) -> Unit
    )

    fun drawVanillaSelectableList(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        items: List<String>,
        selectedIndex: Int,
        rowHeight: Int,
        onSelectedIndexChange: (Int) -> Unit
    )

    fun registerInputTarget(target: InputTarget)

    fun withClipRect(rect: Rect, block: () -> Unit)
}


