package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.LazyColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

/** Number of off-screen items composed on each side so scrolling never shows empty rows. */
private const val OVERSCAN_ITEMS = 2

@LayoutScopeMarker
interface LazyListScope {
  fun item(content: @Composable () -> Unit)

  fun items(count: Int, content: @Composable (index: Int) -> Unit)
}

inline fun <T> LazyListScope.items(
    items: List<T>,
    crossinline content: @Composable (item: T) -> Unit,
) {
  items(items.size) { index -> content(items[index]) }
}

inline fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    crossinline content: @Composable (index: Int, item: T) -> Unit,
) {
  items(items.size) { index -> content(index, items[index]) }
}

@Composable
fun rememberLazyListState(initialScroll: Int = 0): LazyListState =
    rememberSaveable(saver = LazyListState.Saver) {
      LazyListState(initialScroll)
    }

/**
 * Vertical list that only composes the items around the visible window.
 *
 * With [itemHeight] every item occupies that height and any position is known up front. Without it
 * items are measured as they are composed and their heights remembered per index; the average of
 * what has been seen stands in for the rest, so the scrollbar is an estimate until the whole list
 * has been scrolled through once.
 */
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemHeight: UiUnit? = null,
    content: LazyListScope.() -> Unit,
) {
  val entries = LazyListEntries().apply(content)
  val itemCount = entries.count
  val firstIndex =
      (state.firstVisibleItemIndex - OVERSCAN_ITEMS).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
  val lastIndex =
      (state.firstVisibleItemIndex + state.visibleItemCount + OVERSCAN_ITEMS).coerceAtMost(
          itemCount - 1
      )

  ComposeNode<LazyColumnNode, NodeApplier>(
      factory = {
        LazyColumnNode(
            modifier = modifier,
            state = state,
            itemHeight = itemHeight,
            itemCount = itemCount,
            firstIndex = firstIndex,
        )
      },
      update = {
        set(modifier) { this.modifier = it }
        set(state) { this.state = it }
        set(itemHeight) { this.itemHeight = it }
        set(itemCount) { this.itemCount = it }
        set(firstIndex) { this.firstIndex = it }
      },
      content = {
        for (index in firstIndex..lastIndex) {
          key(index) {
            entries.compose(index)
          }
        }
      },
  )
}

private class LazyListEntries : LazyListScope {
  private class Section(val count: Int, val content: @Composable (Int) -> Unit)

  private val sections = mutableListOf<Section>()

  val count: Int
    get() = sections.sumOf { it.count }

  override fun item(content: @Composable () -> Unit) {
    sections += Section(1) { content() }
  }

  override fun items(count: Int, content: @Composable (index: Int) -> Unit) {
    if (count > 0) {
      sections += Section(count, content)
    }
  }

  @Composable
  fun compose(index: Int) {
    var remaining = index
    for (section in sections) {
      if (remaining < section.count) {
        section.content(remaining)
        return
      }
      remaining -= section.count
    }
  }
}
