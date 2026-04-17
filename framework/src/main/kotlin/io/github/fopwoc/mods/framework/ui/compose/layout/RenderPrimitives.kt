package io.github.fopwoc.mods.framework.ui.compose.layout

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

    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int)

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int)

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int)

    fun drawText(text: String, x: Int, y: Int, color: Int, shadow: Boolean)

    fun drawVanillaButton(
        bounds: Rect,
        hostKey: Any,
        text: String,
        enabled: Boolean,
        onClick: () -> Unit
    )

    fun drawVanillaCheckbox(bounds: Rect, label: String, checked: Boolean, enabled: Boolean)

    fun drawVanillaTextField(
        bounds: Rect,
        state: TextFieldState,
        placeholder: String,
        enabled: Boolean,
        style: TextFieldStyle
    )

    fun drawVanillaSlider(
        bounds: Rect,
        hostKey: Any,
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
        hostKey: Any,
        items: List<String>,
        selectedIndex: Int,
        rowHeight: Int,
        onSelectedIndexChange: (Int) -> Unit
    )

    fun withClipRect(rect: Rect, block: () -> Unit)
}


