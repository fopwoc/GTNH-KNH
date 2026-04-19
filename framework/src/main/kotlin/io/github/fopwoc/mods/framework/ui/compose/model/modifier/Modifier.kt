package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import androidx.compose.runtime.Stable
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

enum class ScrollDirection {
    VERTICAL,
    HORIZONTAL
}

@Stable
sealed interface Modifier {
    val padding: PaddingValues
    val fillMaxWidth: Boolean
    val fillMaxHeight: Boolean
    val fixedWidth: UiUnit?
    val fixedHeight: UiUnit?
    val backgroundColor: Color?
    val borderColor: Color?
    val tooltipLines: List<StyledText>?
    val offsetX: UiUnit
    val offsetY: UiUnit

    companion object : Modifier {
        override val padding: PaddingValues = PaddingValues.Zero
        override val fillMaxWidth: Boolean = false
        override val fillMaxHeight: Boolean = false
        override val fixedWidth: UiUnit? = null
        override val fixedHeight: UiUnit? = null
        override val backgroundColor: Color? = null
        override val borderColor: Color? = null
        override val tooltipLines: List<StyledText>? = null
        override val offsetX: UiUnit = UiUnit(0)
        override val offsetY: UiUnit = UiUnit(0)

        override fun toString(): String = "Modifier"
    }

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

    fun background(color: Color): Modifier = copyOf(backgroundColor = color)

    fun border(color: Color): Modifier = copyOf(borderColor = color)

    fun tooltip(text: String): Modifier = copyOf(
        tooltipLines = text.takeIf(String::isNotEmpty)?.let(StyledText::of)?.let(::listOf)
    )

    fun tooltip(lines: List<String>): Modifier = copyOf(
        tooltipLines = lines.takeIf(List<String>::isNotEmpty)?.map(StyledText::of)
    )

    fun tooltip(text: StyledText): Modifier = copyOf(
        tooltipLines = text.takeIf { it != StyledText.Empty }?.let(::listOf)
    )

    fun tooltip(vararg lines: StyledText): Modifier = copyOf(
        tooltipLines = lines
            .filter { it != StyledText.Empty }
            .takeIf(List<StyledText>::isNotEmpty)
    )

    fun offset(x: UiUnit = UiUnit(0), y: UiUnit = UiUnit(0)): Modifier = copyOf(
        offsetX = x,
        offsetY = y
    )

    fun verticalScroll(state: ScrollState): Modifier = copyOf(
        scrollState = state,
        scrollDirection = ScrollDirection.VERTICAL
    )

    fun horizontalScroll(state: ScrollState): Modifier = copyOf(
        scrollState = state,
        scrollDirection = ScrollDirection.HORIZONTAL
    )
}

internal data class ModifierValue(
    override val padding: PaddingValues,
    override val fillMaxWidth: Boolean,
    override val fillMaxHeight: Boolean,
    override val fixedWidth: UiUnit?,
    override val fixedHeight: UiUnit?,
    override val backgroundColor: Color?,
    override val borderColor: Color?,
    override val tooltipLines: List<StyledText>?,
    override val offsetX: UiUnit,
    override val offsetY: UiUnit,
    val parentData: Map<ParentDataKey<*>, Any>,
    val scrollState: ScrollState?,
    val scrollDirection: ScrollDirection?
) : Modifier

internal fun modifierOf(
    padding: PaddingValues = PaddingValues.Zero,
    fillMaxWidth: Boolean = false,
    fillMaxHeight: Boolean = false,
    fixedWidth: UiUnit? = null,
    fixedHeight: UiUnit? = null,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    tooltipLines: List<StyledText>? = null,
    parentData: Map<ParentDataKey<*>, Any> = emptyMap(),
    offsetX: UiUnit = UiUnit(0),
    offsetY: UiUnit = UiUnit(0),
    scrollState: ScrollState? = null,
    scrollDirection: ScrollDirection? = null
): Modifier {
    if (
        padding == PaddingValues.Zero &&
        !fillMaxWidth &&
        !fillMaxHeight &&
        fixedWidth == null &&
        fixedHeight == null &&
        backgroundColor == null &&
        borderColor == null &&
        tooltipLines == null &&
        parentData.isEmpty() &&
        offsetX == UiUnit(0) &&
        offsetY == UiUnit(0) &&
        scrollState == null &&
        scrollDirection == null
    ) {
        return Modifier
    }

    return ModifierValue(
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
        offsetY = offsetY,
        scrollState = scrollState,
        scrollDirection = scrollDirection
    )
}

internal fun Modifier.copyOf(
    padding: PaddingValues = this.padding,
    fillMaxWidth: Boolean = this.fillMaxWidth,
    fillMaxHeight: Boolean = this.fillMaxHeight,
    fixedWidth: UiUnit? = this.fixedWidth,
    fixedHeight: UiUnit? = this.fixedHeight,
    backgroundColor: Color? = this.backgroundColor,
    borderColor: Color? = this.borderColor,
    tooltipLines: List<StyledText>? = this.tooltipLines,
    parentData: Map<ParentDataKey<*>, Any> = this.parentData,
    offsetX: UiUnit = this.offsetX,
    offsetY: UiUnit = this.offsetY,
    scrollState: ScrollState? = this.scrollState,
    scrollDirection: ScrollDirection? = this.scrollDirection
): Modifier = modifierOf(
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
    offsetY = offsetY,
    scrollState = scrollState,
    scrollDirection = scrollDirection
)

internal val Modifier.parentData: Map<ParentDataKey<*>, Any>
    get() = when (this) {
        Modifier -> emptyMap()
        is ModifierValue -> parentData
    }

internal val Modifier.scrollState: ScrollState?
    get() = when (this) {
        Modifier -> null
        is ModifierValue -> scrollState
    }

internal val Modifier.scrollDirection: ScrollDirection?
    get() = when (this) {
        Modifier -> null
        is ModifierValue -> scrollDirection
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

internal val Modifier.verticalScrollState: ScrollState?
    get() = scrollState.takeIf { scrollDirection == ScrollDirection.VERTICAL }

internal val Modifier.horizontalScrollState: ScrollState?
    get() = scrollState.takeIf { scrollDirection == ScrollDirection.HORIZONTAL }

