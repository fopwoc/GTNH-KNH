package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment

data class Modifier(
    val padding: PaddingValues = PaddingValues.Zero,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val fixedWidth: Int? = null,
    val fixedHeight: Int? = null,
    val backgroundColor: Int? = null,
    val borderColor: Int? = null,
    val alignHorizontal: HorizontalAlignment = HorizontalAlignment.START,
    val alignVertical: VerticalAlignment = VerticalAlignment.TOP,
    val offsetX: Int = 0,
    val offsetY: Int = 0
) {
    fun padding(all: Int): Modifier = copy(
        padding = PaddingValues(all, all, all, all)
    )

    fun padding(horizontal: Int = 0, vertical: Int = 0): Modifier = copy(
        padding = PaddingValues(horizontal, vertical, horizontal, vertical)
    )

    fun padding(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): Modifier = copy(
        padding = PaddingValues(left, top, right, bottom)
    )

    fun fillMaxWidth(): Modifier = copy(fillMaxWidth = true)

    fun fillMaxHeight(): Modifier = copy(fillMaxHeight = true)

    fun fillMaxSize(): Modifier = copy(
        fillMaxWidth = true,
        fillMaxHeight = true
    )

    fun width(width: Int): Modifier = copy(fixedWidth = width)

    fun height(height: Int): Modifier = copy(fixedHeight = height)

    fun size(width: Int, height: Int): Modifier = copy(
        fixedWidth = width,
        fixedHeight = height
    )

    fun background(color: Int): Modifier = copy(backgroundColor = color)

    fun border(color: Int): Modifier = copy(borderColor = color)

    fun align(
        horizontal: HorizontalAlignment = HorizontalAlignment.START,
        vertical: VerticalAlignment = VerticalAlignment.TOP
    ): Modifier = copy(
        alignHorizontal = horizontal,
        alignVertical = vertical
    )

    fun offset(x: Int = 0, y: Int = 0): Modifier = copy(
        offsetX = x,
        offsetY = y
    )
}

