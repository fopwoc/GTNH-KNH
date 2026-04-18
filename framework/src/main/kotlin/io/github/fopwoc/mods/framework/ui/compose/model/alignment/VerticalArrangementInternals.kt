package io.github.fopwoc.mods.framework.ui.compose.model.alignment

import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

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

internal fun VerticalArrangement.arrange(totalSize: Int, childSizes: List<Int>): IntArray {
    return arrangeMainAxis(
        totalSize = totalSize,
        childSizes = childSizes,
        leadingAlignment = verticalLeadingAlignment(),
        spacing = measuredSpacing(childSizes.size),
        distribution = verticalMainAxisDistribution()
    )
}

private fun VerticalArrangement.verticalLeadingAlignment(): LeadingAlignment {
    return when (this) {
        VerticalArrangement.Top -> LeadingAlignment.START
        VerticalArrangement.Center -> LeadingAlignment.CENTER
        VerticalArrangement.Bottom -> LeadingAlignment.END
        is VerticalArrangement.SpacedBy -> when (alignment) {
            VerticalAlignment.TOP -> LeadingAlignment.START
            VerticalAlignment.CENTER -> LeadingAlignment.CENTER
            VerticalAlignment.BOTTOM -> LeadingAlignment.END
        }
        VerticalArrangement.SpaceBetween,
        VerticalArrangement.SpaceAround,
        VerticalArrangement.SpaceEvenly -> LeadingAlignment.START
    }
}

private fun VerticalArrangement.verticalMainAxisDistribution(): MainAxisDistribution {
    return when (this) {
        VerticalArrangement.SpaceBetween -> MainAxisDistribution.SPACE_BETWEEN
        VerticalArrangement.SpaceAround -> MainAxisDistribution.SPACE_AROUND
        VerticalArrangement.SpaceEvenly -> MainAxisDistribution.SPACE_EVENLY
        VerticalArrangement.Top,
        VerticalArrangement.Center,
        VerticalArrangement.Bottom,
        is VerticalArrangement.SpacedBy -> MainAxisDistribution.ALIGNED
    }
}

