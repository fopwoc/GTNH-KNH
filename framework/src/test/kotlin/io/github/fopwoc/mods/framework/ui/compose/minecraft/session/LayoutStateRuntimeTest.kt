package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeMainDispatcherBridge
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Drives [ComposeGuiRuntime] and [ComposeRenderLayoutState] the same way [ComposeRenderSession]
 * does, without Minecraft: the snapshot apply observer is the only thing allowed to invalidate the
 * cached layout between frames.
 */
class LayoutStateRuntimeTest {
  @AfterTest
  fun tearDown() {
    ComposeMainDispatcherBridge.resetForTests()
  }

  @Test
  fun stateChangeRefreshesCachedLayoutThroughApplyObserver() {
    val layoutState = ComposeRenderLayoutState()
    val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
    val root = RootNode()
    var label by mutableStateOf("before")
    var enabled by mutableStateOf(true)

    runtime.start(root) {
      Column {
        Text(label)
        Button(text = "Run", enabled = enabled, onClick = {})
      }
    }

    try {
      val first = frame(runtime, layoutState, root)
      assertEquals(listOf("before"), first.texts())
      assertEquals(true, first.buttonEnabled())

      enabled = false
      val second = frame(runtime, layoutState, root)
      assertEquals(false, second.buttonEnabled())
      assertSame(first, second, "shape-equivalent change must refresh the existing layout tree")

      label = "after"
      val third = frame(runtime, layoutState, root)
      assertEquals(listOf("after"), third.texts())
      assertNotSame(second, third, "text change alters measurement and must relayout")

      assertSame(third, frame(runtime, layoutState, root), "idle frames reuse the layout")
    } finally {
      runtime.dispose()
    }
  }

  @Test
  fun coroutineFailureIsRethrownOnNextFrame() {
    val runtime = ComposeGuiRuntime(onCompositionChanged = {})
    runtime.start(RootNode()) {
      LaunchedEffect(Unit) {
        error("effect exploded")
      }
    }

    try {
      runtime.pump()
      val failure = assertFailsWith<IllegalStateException> { runtime.rethrowPendingFailure() }
      assertEquals("effect exploded", failure.cause?.message)
      runtime.rethrowPendingFailure()
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

  private fun LayoutNode.buttonEnabled(): Boolean? {
    fun visit(node: LayoutNode): Boolean? =
        (node.element as? LayoutElement.Button)?.enabled
            ?: node.children.firstNotNullOfOrNull(::visit)
    return visit(this)
  }

  private object Metrics : TextMetrics {
    override val lineHeight: Int = 9

    override fun textWidth(text: String): Int = text.length * 6

    override fun wrapText(text: String, maxWidth: Int): List<String> = listOf(text)
  }
}
