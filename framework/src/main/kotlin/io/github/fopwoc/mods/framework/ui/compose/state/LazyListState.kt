package io.github.fopwoc.mods.framework.ui.compose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * Scroll position of a `LazyColumn`. Scrolling is pixel based so the shared wheel/thumb machinery
 * applies; item heights are either fixed or measured as items get composed and remembered per
 * index, with the running average standing in for items not seen yet.
 */
@Stable
class LazyListState(initialScroll: Int = 0) {
  val scroll: ScrollState = ScrollState(initialScroll)

  var firstVisibleItemIndex: Int by mutableStateOf(0)
    internal set

  var visibleItemCount: Int by mutableStateOf(0)
    internal set

  private var fixedItemHeight: Int? = null
  private val measuredHeights = HashMap<Int, Int>()
  private var measuredTotal = 0L

  internal var itemCount: Int = 0

  val scrollOffset: Int
    get() = scroll.value

  /** Index a `scrollToItem` is still homing in on while heights ahead of it get measured. */
  private var pendingTargetIndex: Int? = null

  /**
   * Scrolls so [index] is the first visible row. With measured heights the first jump is an
   * estimate; the position is corrected on the following layouts until the row is on top.
   */
  fun scrollToItem(index: Int): Boolean {
    val target = index.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    pendingTargetIndex = target
    return scroll.scrollTo(offsetOf(target))
  }

  fun scrollBy(delta: Int): Boolean {
    pendingTargetIndex = null
    pendingStableLayouts = 0
    return scroll.scrollBy(delta)
  }

  private var pendingStableLayouts = 0

  /** Re-aims a pending `scrollToItem` with the heights known now; true when it moved. */
  internal fun settlePendingTarget(): Boolean {
    val target = pendingTargetIndex ?: return false
    val offset = offsetOf(target)
    return scroll.value != offset && scroll.scrollTo(offset)
  }

  /** Called with the first visible row of a layout; drops the target once it has settled. */
  internal fun notePendingResult(firstVisible: Int) {
    val target = pendingTargetIndex ?: return
    pendingStableLayouts = if (firstVisible == target) pendingStableLayouts + 1 else 0
    if (pendingStableLayouts >= 2) {
      pendingTargetIndex = null
      pendingStableLayouts = 0
    }
  }

  internal fun useFixedHeight(height: Int) {
    fixedItemHeight = height.coerceAtLeast(1)
  }

  internal fun useMeasuredHeights() {
    fixedItemHeight = null
  }

  internal fun recordHeight(index: Int, height: Int) {
    val clamped = height.coerceAtLeast(1)
    val previous = measuredHeights.put(index, clamped)
    measuredTotal += clamped - (previous ?: 0)
  }

  internal val averageItemHeight: Int
    get() =
        fixedItemHeight
            ?: if (measuredHeights.isEmpty()) DEFAULT_ESTIMATE
            else (measuredTotal / measuredHeights.size).toInt().coerceAtLeast(1)

  internal fun heightOf(index: Int): Int =
      fixedItemHeight ?: measuredHeights[index] ?: averageItemHeight

  internal fun contentHeight(): Int {
    fixedItemHeight?.let {
      return it * itemCount
    }
    val known = measuredHeights.keys.count { it < itemCount }
    return measuredTotal.toInt() + (itemCount - known).coerceAtLeast(0) * averageItemHeight
  }

  /** Pixel offset of the top of [index]. */
  internal fun offsetOf(index: Int): Int {
    fixedItemHeight?.let {
      return it * index
    }
    var offset = 0
    for (i in 0 until index) {
      offset += heightOf(i)
    }
    return offset
  }

  /** Index of the item covering pixel [offset]. */
  internal fun indexAt(offset: Int): Int {
    if (itemCount <= 0) {
      return 0
    }
    fixedItemHeight?.let {
      return (offset / it).coerceIn(0, itemCount - 1)
    }
    var remaining = offset
    for (i in 0 until itemCount) {
      val height = heightOf(i)
      if (remaining < height) {
        return i
      }
      remaining -= height
    }
    return itemCount - 1
  }

  companion object {
    private const val DEFAULT_ESTIMATE = 12

    val Saver: Saver<LazyListState, Int> =
        Saver(
            save = { it.scroll.value },
            restore = { LazyListState(initialScroll = it) },
        )
  }
}
