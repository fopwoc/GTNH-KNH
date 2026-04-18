package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

class Modifier internal constructor(
    val padding: PaddingValues,
    val fillMaxWidth: Boolean,
    val fillMaxHeight: Boolean,
    val fixedWidth: UiUnit?,
    val fixedHeight: UiUnit?,
    val backgroundColor: Int?,
    val borderColor: Int?,
    val tooltipLines: List<String>?,
    internal val parentData: Map<ParentDataKey<*>, Any>,
    val offsetX: UiUnit,
    val offsetY: UiUnit
) {
    constructor() : this(
        padding = PaddingValues.Zero,
        fillMaxWidth = false,
        fillMaxHeight = false,
        fixedWidth = null,
        fixedHeight = null,
        backgroundColor = null,
        borderColor = null,
        tooltipLines = null,
        parentData = emptyMap(),
        offsetX = UiUnit(0),
        offsetY = UiUnit(0)
    )

    fun padding(all: UiUnit): Modifier = copyOf(
        padding = PaddingValues(all, all, all, all)
    )

    fun padding(horizontal: UiUnit = UiUnit(0), vertical: UiUnit = UiUnit(0)): Modifier = copyOf(
        padding = PaddingValues(horizontal, vertical, horizontal, vertical)
    )

    fun padding(
        left: UiUnit = UiUnit(0),
        top: UiUnit = UiUnit(0),
        right: UiUnit = UiUnit(0),
        bottom: UiUnit = UiUnit(0)
    ): Modifier = copyOf(
        padding = PaddingValues(left, top, right, bottom)
    )

    fun fillMaxWidth(): Modifier = copyOf(fillMaxWidth = true)

    fun fillMaxHeight(): Modifier = copyOf(fillMaxHeight = true)

    fun fillMaxSize(): Modifier = copyOf(
        fillMaxWidth = true,
        fillMaxHeight = true
    )

    fun width(width: UiUnit): Modifier = copyOf(fixedWidth = width)

    fun height(height: UiUnit): Modifier = copyOf(fixedHeight = height)

    fun size(width: UiUnit, height: UiUnit): Modifier = copyOf(
        fixedWidth = width,
        fixedHeight = height
    )

    fun size(size: UiUnit): Modifier = size(size, size)

    fun background(color: Int): Modifier = copyOf(backgroundColor = color)

    fun border(color: Int): Modifier = copyOf(borderColor = color)

    fun tooltip(text: String): Modifier = copyOf(
        tooltipLines = text.takeIf(String::isNotEmpty)?.let(::listOf)
    )

    fun tooltip(lines: List<String>): Modifier = copyOf(
        tooltipLines = lines.takeIf(List<String>::isNotEmpty)?.toList()
    )

    fun offset(x: UiUnit = UiUnit(0), y: UiUnit = UiUnit(0)): Modifier = copyOf(
        offsetX = x,
        offsetY = y
    )

    internal fun copyOf(
        padding: PaddingValues = this.padding,
        fillMaxWidth: Boolean = this.fillMaxWidth,
        fillMaxHeight: Boolean = this.fillMaxHeight,
        fixedWidth: UiUnit? = this.fixedWidth,
        fixedHeight: UiUnit? = this.fixedHeight,
        backgroundColor: Int? = this.backgroundColor,
        borderColor: Int? = this.borderColor,
        tooltipLines: List<String>? = this.tooltipLines,
        parentData: Map<ParentDataKey<*>, Any> = this.parentData,
        offsetX: UiUnit = this.offsetX,
        offsetY: UiUnit = this.offsetY
    ): Modifier = Modifier(
        padding = padding,
        fillMaxWidth = fillMaxWidth,
        fillMaxHeight = fillMaxHeight,
        fixedWidth = fixedWidth,
        fixedHeight = fixedHeight,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        tooltipLines = tooltipLines,
        parentData = parentData,
        offsetX = offsetX,
        offsetY = offsetY
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Modifier) {
            return false
        }

        return padding == other.padding
            && fillMaxWidth == other.fillMaxWidth
            && fillMaxHeight == other.fillMaxHeight
            && fixedWidth == other.fixedWidth
            && fixedHeight == other.fixedHeight
            && backgroundColor == other.backgroundColor
            && borderColor == other.borderColor
            && tooltipLines == other.tooltipLines
            && parentData == other.parentData
            && offsetX == other.offsetX
            && offsetY == other.offsetY
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + fillMaxWidth.hashCode()
        result = 31 * result + fillMaxHeight.hashCode()
        result = 31 * result + (fixedWidth?.hashCode() ?: 0)
        result = 31 * result + (fixedHeight?.hashCode() ?: 0)
        result = 31 * result + (backgroundColor ?: 0)
        result = 31 * result + (borderColor ?: 0)
        result = 31 * result + (tooltipLines?.hashCode() ?: 0)
        result = 31 * result + parentData.hashCode()
        result = 31 * result + offsetX.hashCode()
        result = 31 * result + offsetY.hashCode()
        return result
    }

    override fun toString(): String {
        return "Modifier(padding=$padding, fillMaxWidth=$fillMaxWidth, fillMaxHeight=$fillMaxHeight, fixedWidth=$fixedWidth, fixedHeight=$fixedHeight, backgroundColor=$backgroundColor, borderColor=$borderColor, tooltipLines=$tooltipLines, offsetX=$offsetX, offsetY=$offsetY)"
    }
}

internal fun <T : Any> Modifier.withParentData(
    key: ParentDataKey<T>,
    defaultValue: () -> T,
    transform: (T) -> T
): Modifier {
    @Suppress("UNCHECKED_CAST")
    val currentValue = parentData[key] as? T ?: defaultValue()
    return copyOf(
        parentData = parentData + (key to transform(currentValue))
    )
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Modifier.parentDataOrNull(key: ParentDataKey<T>): T? = parentData[key] as? T

internal val Modifier.resolvedFixedWidth: Int?
    get() = fixedWidth?.resolved

internal val Modifier.resolvedFixedHeight: Int?
    get() = fixedHeight?.resolved

internal val Modifier.resolvedOffsetX: Int
    get() = offsetX.resolved

internal val Modifier.resolvedOffsetY: Int
    get() = offsetY.resolved

