package io.github.fopwoc.mods.framework.ui.compose.model.alignment

import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import kotlin.math.roundToInt

sealed interface HorizontalArrangement {
    object Start : HorizontalArrangement
    object Center : HorizontalArrangement
    object End : HorizontalArrangement
    object SpaceBetween : HorizontalArrangement
    object SpaceAround : HorizontalArrangement
    object SpaceEvenly : HorizontalArrangement

    data class SpacedBy(
        val space: UiUnit,
        val alignment: HorizontalAlignment = HorizontalAlignment.START
    ) : HorizontalArrangement

    companion object {
        fun spacedBy(
            space: UiUnit,
            alignment: HorizontalAlignment = HorizontalAlignment.START
        ): HorizontalArrangement = SpacedBy(space, alignment)
    }
}

sealed interface VerticalArrangement {
    object Top : VerticalArrangement
    object Center : VerticalArrangement
    object Bottom : VerticalArrangement
    object SpaceBetween : VerticalArrangement
    object SpaceAround : VerticalArrangement
    object SpaceEvenly : VerticalArrangement

    data class SpacedBy(
        val space: UiUnit,
        val alignment: VerticalAlignment = VerticalAlignment.TOP
    ) : VerticalArrangement

    companion object {
        fun spacedBy(
            space: UiUnit,
            alignment: VerticalAlignment = VerticalAlignment.TOP
        ): VerticalArrangement = SpacedBy(space, alignment)
    }
}

internal fun HorizontalArrangement.measuredSpacing(childCount: Int): Int {
    if (childCount <= 1) {
        return 0
    }

    return when (this) {
        is HorizontalArrangement.SpacedBy -> space.resolved
        HorizontalArrangement.Start,
        HorizontalArrangement.Center,
        HorizontalArrangement.End,
        HorizontalArrangement.SpaceBetween,
        HorizontalArrangement.SpaceAround,
        HorizontalArrangement.SpaceEvenly -> 0
    }
}

internal fun VerticalArrangement.measuredSpacing(childCount: Int): Int {
    if (childCount <= 1) {
        return 0
    }

    return when (this) {
        is VerticalArrangement.SpacedBy -> space.resolved
        VerticalArrangement.Top,
        VerticalArrangement.Center,
        VerticalArrangement.Bottom,
        VerticalArrangement.SpaceBetween,
        VerticalArrangement.SpaceAround,
        VerticalArrangement.SpaceEvenly -> 0
    }
}

internal fun HorizontalArrangement.arrange(totalSize: Int, childSizes: List<Int>): IntArray {
    return arrangeMainAxis(
        totalSize = totalSize,
        childSizes = childSizes,
        leadingAlignment = when (this) {
            HorizontalArrangement.Start -> LeadingAlignment.START
            HorizontalArrangement.Center -> LeadingAlignment.CENTER
            HorizontalArrangement.End -> LeadingAlignment.END
            is HorizontalArrangement.SpacedBy -> when (alignment) {
                HorizontalAlignment.START -> LeadingAlignment.START
                HorizontalAlignment.CENTER -> LeadingAlignment.CENTER
                HorizontalAlignment.END -> LeadingAlignment.END
            }
            HorizontalArrangement.SpaceBetween -> LeadingAlignment.START
            HorizontalArrangement.SpaceAround -> LeadingAlignment.START
            HorizontalArrangement.SpaceEvenly -> LeadingAlignment.START
        },
        spacing = measuredSpacing(childSizes.size),
        distribution = when (this) {
            HorizontalArrangement.SpaceBetween -> MainAxisDistribution.SPACE_BETWEEN
            HorizontalArrangement.SpaceAround -> MainAxisDistribution.SPACE_AROUND
            HorizontalArrangement.SpaceEvenly -> MainAxisDistribution.SPACE_EVENLY
            HorizontalArrangement.Start,
            HorizontalArrangement.Center,
            HorizontalArrangement.End,
            is HorizontalArrangement.SpacedBy -> MainAxisDistribution.ALIGNED
        }
    )
}

internal fun VerticalArrangement.arrange(totalSize: Int, childSizes: List<Int>): IntArray {
    return arrangeMainAxis(
        totalSize = totalSize,
        childSizes = childSizes,
        leadingAlignment = when (this) {
            VerticalArrangement.Top -> LeadingAlignment.START
            VerticalArrangement.Center -> LeadingAlignment.CENTER
            VerticalArrangement.Bottom -> LeadingAlignment.END
            is VerticalArrangement.SpacedBy -> when (alignment) {
                VerticalAlignment.TOP -> LeadingAlignment.START
                VerticalAlignment.CENTER -> LeadingAlignment.CENTER
                VerticalAlignment.BOTTOM -> LeadingAlignment.END
            }
            VerticalArrangement.SpaceBetween -> LeadingAlignment.START
            VerticalArrangement.SpaceAround -> LeadingAlignment.START
            VerticalArrangement.SpaceEvenly -> LeadingAlignment.START
        },
        spacing = measuredSpacing(childSizes.size),
        distribution = when (this) {
            VerticalArrangement.SpaceBetween -> MainAxisDistribution.SPACE_BETWEEN
            VerticalArrangement.SpaceAround -> MainAxisDistribution.SPACE_AROUND
            VerticalArrangement.SpaceEvenly -> MainAxisDistribution.SPACE_EVENLY
            VerticalArrangement.Top,
            VerticalArrangement.Center,
            VerticalArrangement.Bottom,
            is VerticalArrangement.SpacedBy -> MainAxisDistribution.ALIGNED
        }
    )
}

private enum class LeadingAlignment {
    START,
    CENTER,
    END
}

private enum class MainAxisDistribution {
    ALIGNED,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY
}

private fun arrangeMainAxis(
    totalSize: Int,
    childSizes: List<Int>,
    leadingAlignment: LeadingAlignment,
    spacing: Int,
    distribution: MainAxisDistribution
): IntArray {
    if (childSizes.isEmpty()) {
        return IntArray(0)
    }

    val occupiedSize = childSizes.sum() + if (childSizes.size > 1) spacing * (childSizes.size - 1) else 0
    val freeSpace = (totalSize - occupiedSize).coerceAtLeast(0)
    return when (distribution) {
        MainAxisDistribution.ALIGNED -> arrangeAligned(childSizes, freeSpace, spacing, leadingAlignment)
        MainAxisDistribution.SPACE_BETWEEN -> arrangeDistributed(childSizes, leadingSpace = 0.0, gapSpace = if (childSizes.size > 1) freeSpace.toDouble() / (childSizes.size - 1).toDouble() else 0.0)
        MainAxisDistribution.SPACE_AROUND -> arrangeDistributed(childSizes, leadingSpace = if (childSizes.isNotEmpty()) freeSpace.toDouble() / childSizes.size.toDouble() / 2.0 else 0.0, gapSpace = if (childSizes.isNotEmpty()) freeSpace.toDouble() / childSizes.size.toDouble() else 0.0)
        MainAxisDistribution.SPACE_EVENLY -> arrangeDistributed(childSizes, leadingSpace = freeSpace.toDouble() / (childSizes.size + 1).toDouble(), gapSpace = freeSpace.toDouble() / (childSizes.size + 1).toDouble())
    }
}

private fun arrangeAligned(
    childSizes: List<Int>,
    freeSpace: Int,
    spacing: Int,
    leadingAlignment: LeadingAlignment
): IntArray {
    val leadingSpace = when (leadingAlignment) {
        LeadingAlignment.START -> 0
        LeadingAlignment.CENTER -> freeSpace / 2
        LeadingAlignment.END -> freeSpace
    }
    val positions = IntArray(childSizes.size)
    var currentPosition = leadingSpace
    childSizes.forEachIndexed { index, childSize ->
        positions[index] = currentPosition
        currentPosition += childSize
        if (index < childSizes.lastIndex) {
            currentPosition += spacing
        }
    }
    return positions
}

private fun arrangeDistributed(
    childSizes: List<Int>,
    leadingSpace: Double,
    gapSpace: Double
): IntArray {
    val positions = IntArray(childSizes.size)
    var currentPosition = leadingSpace
    childSizes.forEachIndexed { index, childSize ->
        positions[index] = currentPosition.roundToInt()
        currentPosition += childSize
        if (index < childSizes.lastIndex) {
            currentPosition += gapSpace
        }
    }
    return positions
}

