package io.github.fopwoc.mods.framework.ui.compose.model.alignment

import kotlin.math.roundToInt

internal enum class LeadingAlignment {
  START,
  CENTER,
  END,
}

internal enum class MainAxisDistribution {
  ALIGNED,
  SPACE_BETWEEN,
  SPACE_AROUND,
  SPACE_EVENLY,
}

internal fun arrangeMainAxis(
    totalSize: Int,
    childSizes: List<Int>,
    leadingAlignment: LeadingAlignment,
    spacing: Int,
    distribution: MainAxisDistribution,
): IntArray {
  if (childSizes.isEmpty()) {
    return IntArray(0)
  }

  val occupiedSize =
      childSizes.sum() + if (childSizes.size > 1) spacing * (childSizes.size - 1) else 0
  val freeSpace = (totalSize - occupiedSize).coerceAtLeast(0)
  return when (distribution) {
    MainAxisDistribution.ALIGNED -> arrangeAligned(childSizes, freeSpace, spacing, leadingAlignment)
    MainAxisDistribution.SPACE_BETWEEN ->
        arrangeDistributed(
            childSizes,
            leadingSpace = 0.0,
            gapSpace =
                if (childSizes.size > 1) freeSpace.toDouble() / (childSizes.size - 1).toDouble()
                else 0.0,
        )
    MainAxisDistribution.SPACE_AROUND ->
        arrangeDistributed(
            childSizes,
            leadingSpace =
                if (childSizes.isNotEmpty()) freeSpace.toDouble() / childSizes.size.toDouble() / 2.0
                else 0.0,
            gapSpace =
                if (childSizes.isNotEmpty()) freeSpace.toDouble() / childSizes.size.toDouble()
                else 0.0,
        )
    MainAxisDistribution.SPACE_EVENLY ->
        arrangeDistributed(
            childSizes,
            leadingSpace = freeSpace.toDouble() / (childSizes.size + 1).toDouble(),
            gapSpace = freeSpace.toDouble() / (childSizes.size + 1).toDouble(),
        )
  }
}

private fun arrangeAligned(
    childSizes: List<Int>,
    freeSpace: Int,
    spacing: Int,
    leadingAlignment: LeadingAlignment,
): IntArray {
  val leadingSpace =
      when (leadingAlignment) {
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
    gapSpace: Double,
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
