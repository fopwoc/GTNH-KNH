package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

data class Modifier(
    val padding: PaddingValues = PaddingValues.Zero,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val fixedWidth: UiUnit? = null,
    val fixedHeight: UiUnit? = null,
    val backgroundColor: Int? = null,
    val borderColor: Int? = null,
    val alignment: Alignment? = null,
    val matchParentSize: Boolean = false,
    val offsetX: UiUnit = UiUnit(0),
    val offsetY: UiUnit = UiUnit(0)
) {
    fun padding(all: UiUnit): Modifier = copy(
        padding = PaddingValues(all, all, all, all)
    )

    fun padding(horizontal: UiUnit = UiUnit(0), vertical: UiUnit = UiUnit(0)): Modifier = copy(
        padding = PaddingValues(horizontal, vertical, horizontal, vertical)
    )

    fun padding(
        left: UiUnit = UiUnit(0),
        top: UiUnit = UiUnit(0),
        right: UiUnit = UiUnit(0),
        bottom: UiUnit = UiUnit(0)
    ): Modifier = copy(
        padding = PaddingValues(left, top, right, bottom)
    )

    fun fillMaxWidth(): Modifier = copy(fillMaxWidth = true)

    fun fillMaxHeight(): Modifier = copy(fillMaxHeight = true)

    fun fillMaxSize(): Modifier = copy(
        fillMaxWidth = true,
        fillMaxHeight = true
    )

    fun width(width: UiUnit): Modifier = copy(fixedWidth = width)

    fun height(height: UiUnit): Modifier = copy(fixedHeight = height)

    fun size(width: UiUnit, height: UiUnit): Modifier = copy(
        fixedWidth = width,
        fixedHeight = height
    )

    fun size(size: UiUnit): Modifier = size(size, size)

    fun background(color: Int): Modifier = copy(backgroundColor = color)

    fun border(color: Int): Modifier = copy(borderColor = color)

    fun align(alignment: Alignment): Modifier = copy(alignment = alignment)

    fun matchParentSize(): Modifier = copy(matchParentSize = true)

    fun offset(x: UiUnit = UiUnit(0), y: UiUnit = UiUnit(0)): Modifier = copy(
        offsetX = x,
        offsetY = y
    )
}

internal val Modifier.resolvedFixedWidth: Int?
    get() = fixedWidth?.resolved

internal val Modifier.resolvedFixedHeight: Int?
    get() = fixedHeight?.resolved

internal val Modifier.resolvedOffsetX: Int
    get() = offsetX.resolved

internal val Modifier.resolvedOffsetY: Int
    get() = offsetY.resolved

