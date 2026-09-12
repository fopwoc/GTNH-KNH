package io.github.fopwoc.mods.framework.ui.compose.theme

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import io.github.fopwoc.mods.framework.ui.compose.component.Section
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MinecraftThemeTest {
  private val red = Color(0xFFFF0000)
  private val blue = Color(0xFF0000FF)

  @Test
  fun defaultsApplyWithoutAProviderAndOverridesReachThemedComponents() = runBlocking {
    val root = RootNode()
    val frameClock = BroadcastFrameClock()
    val recomposer = Recomposer(Dispatchers.Unconfined + frameClock)
    val composition = Composition(NodeApplier(root), recomposer)
    val job =
        launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
          recomposer.runRecomposeAndApplyChanges()
        }
    try {
      composition.setContent {
        Text("default", style = MinecraftTheme.typography.muted)
        MinecraftTheme(colors = MinecraftTheme.colors.copy(title = red, surfaceBorder = blue)) {
          Text("themed", style = MinecraftTheme.typography.sectionTitle)
          Section(title = "s") { Text("inner", style = MinecraftTheme.typography.sectionTitle) }
        }
      }
      recomposer.awaitIdle()

      val texts = root.descendants().filterIsInstance<TextNode>().associateBy { it.text.plainText }
      assertEquals(ThemeColors.Default.muted, texts.getValue("default").style.color)
      assertEquals(red, texts.getValue("themed").style.color)
      assertEquals(red, texts.getValue("s").style.color)
      assertEquals(red, texts.getValue("inner").style.color)
      val bordered =
          root.descendants().filterIsInstance<BoxNode>().mapNotNull { it.modifier.borderColor }
      assertEquals(listOf(blue), bordered)
    } finally {
      composition.dispose()
      recomposer.cancel()
      job.cancelAndJoin()
    }
  }

  private fun ComposeTreeNode.descendants(): List<ComposeTreeNode> = children.flatMap {
    listOf(it) + it.descendants()
  }
}
