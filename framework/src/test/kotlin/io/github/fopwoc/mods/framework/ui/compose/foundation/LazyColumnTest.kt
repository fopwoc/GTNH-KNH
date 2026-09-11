package io.github.fopwoc.mods.framework.ui.compose.foundation

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderLayoutState
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeMainDispatcherBridge
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyColumnTest {
  @AfterTest
  fun tearDown() {
    ComposeMainDispatcherBridge.resetForTests()
  }

  @Test
  fun composesOnlyTheVisibleWindowAndFollowsScrolling() {
    val layoutState = ComposeRenderLayoutState()
    val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
    val root = RootNode()
    val listState = LazyListState()

    runtime.start(root) {
      LazyColumn(modifier = Modifier.height(50.uu), state = listState, itemHeight = 10.uu) {
        items(100) { index -> Text("item $index") }
      }
    }

    try {
      // First frame lays out with an unknown viewport; the second uses the published window.
      frame(runtime, layoutState, root)
      val settled = frame(runtime, layoutState, root)
      val list = settled.children.single()

      assertEquals(50, list.bounds.height)
      assertEquals(1000, list.contentMainAxisSize)
      assertEquals(0, listState.firstVisibleItemIndex)
      assertEquals(6, listState.visibleItemCount)
      val texts = settled.texts()
      assertEquals("item 0", texts.first())
      assertTrue(texts.size in 6..10, "composed ${texts.size} items instead of a window")
      assertEquals(list.bounds.y, list.children.first().bounds.y)

      assertTrue(listState.scrollToItem(50))
      frame(runtime, layoutState, root)
      val scrolled = frame(runtime, layoutState, root)

      assertEquals(50, listState.firstVisibleItemIndex)
      val scrolledTexts = scrolled.texts()
      assertTrue("item 50" in scrolledTexts, scrolledTexts.toString())
      assertTrue("item 47" !in scrolledTexts, scrolledTexts.toString())
      val firstVisible =
          scrolled.children.single().children.first {
            it.element is LayoutElement.Text &&
                (it.element as LayoutElement.Text).text.plainText == "item 50"
          }
      assertEquals(scrolled.children.single().bounds.y, firstVisible.bounds.y)
    } finally {
      runtime.dispose()
    }
  }

  private fun frame(
      runtime: ComposeGuiRuntime,
      layoutState: ComposeRenderLayoutState,
      root: RootNode,
  ): LayoutNode {
    runtime.pump()
    runtime.sendFrame(System.nanoTime())
    runtime.pump()
    return layoutState.ensureLayout(root, Metrics, 200, 200)
  }

  private fun LayoutNode.texts(): List<String> {
    val collected = mutableListOf<String>()
    fun visit(node: LayoutNode) {
      (node.element as? LayoutElement.Text)?.let { collected += it.text.plainText }
      node.children.forEach(::visit)
    }
    visit(this)
    return collected
  }

  private object Metrics : TextMetrics {
    override val lineHeight: Int = 9

    override fun textWidth(text: String): Int = text.length * 6

    override fun wrapText(text: String, maxWidth: Int): List<String> = listOf(text)
  }
}
