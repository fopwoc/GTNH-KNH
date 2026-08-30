package io.github.fopwoc.mods.framework.ui.compose.model.alignment

import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

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

internal fun HorizontalArrangement.arrange(totalSize: Int, childSizes: List<Int>): IntArray {
  return arrangeMainAxis(
      totalSize = totalSize,
      childSizes = childSizes,
      leadingAlignment = horizontalLeadingAlignment(),
      spacing = measuredSpacing(childSizes.size),
      distribution = horizontalMainAxisDistribution(),
  )
}

private fun HorizontalArrangement.horizontalLeadingAlignment(): LeadingAlignment {
  return when (this) {
    HorizontalArrangement.Start -> LeadingAlignment.START
    HorizontalArrangement.Center -> LeadingAlignment.CENTER
    HorizontalArrangement.End -> LeadingAlignment.END
    is HorizontalArrangement.SpacedBy ->
        when (alignment) {
          HorizontalAlignment.START -> LeadingAlignment.START
          HorizontalAlignment.CENTER -> LeadingAlignment.CENTER
          HorizontalAlignment.END -> LeadingAlignment.END
        }
    HorizontalArrangement.SpaceBetween,
    HorizontalArrangement.SpaceAround,
    HorizontalArrangement.SpaceEvenly -> LeadingAlignment.START
  }
}

private fun HorizontalArrangement.horizontalMainAxisDistribution(): MainAxisDistribution {
  return when (this) {
    HorizontalArrangement.SpaceBetween -> MainAxisDistribution.SPACE_BETWEEN
    HorizontalArrangement.SpaceAround -> MainAxisDistribution.SPACE_AROUND
    HorizontalArrangement.SpaceEvenly -> MainAxisDistribution.SPACE_EVENLY
    HorizontalArrangement.Start,
    HorizontalArrangement.Center,
    HorizontalArrangement.End,
    is HorizontalArrangement.SpacedBy -> MainAxisDistribution.ALIGNED
  }
}
