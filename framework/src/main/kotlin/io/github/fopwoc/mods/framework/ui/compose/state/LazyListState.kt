package io.github.fopwoc.mods.framework.ui.compose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * Scroll position of a `LazyColumn`. Items have a fixed height, so the visible window is derived
 * from the pixel offset during layout and read back by composition to decide which items exist.
 */
@Stable
class LazyListState(initialScroll: Int = 0) {
  val scroll: ScrollState = ScrollState(initialScroll)

  var firstVisibleItemIndex: Int by mutableStateOf(0)
    internal set

  var visibleItemCount: Int by mutableStateOf(0)
    internal set

  internal var itemHeightPx: Int = 1

  val scrollOffset: Int
    get() = scroll.value

  fun scrollToItem(index: Int): Boolean = scroll.scrollTo(index.coerceAtLeast(0) * itemHeightPx)

  fun scrollBy(delta: Int): Boolean = scroll.scrollBy(delta)

  companion object {
    val Saver: Saver<LazyListState, Int> =
        Saver(
            save = { it.scroll.value },
            restore = { LazyListState(initialScroll = it) },
        )
  }
}
