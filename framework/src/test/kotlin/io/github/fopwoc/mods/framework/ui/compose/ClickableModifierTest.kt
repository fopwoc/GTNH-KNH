package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickableModifierTest {
  private class Recording(override val mouseX: Int = -1, override val mouseY: Int = -1) :
      RenderContext {
    val targets = mutableListOf<InputTarget>()
    val fills = mutableListOf<Color>()
    override val viewportWidth = 200
    override val viewportHeight = 200
    override val lineHeight = 9

    override fun textWidth(text: String) = text.length * 6

    override fun wrapText(text: String, maxWidth: Int) = listOf(text)

    override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) {
      fills += color
    }

    override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) = Unit

    override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) = Unit

    override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) = Unit

    override fun registerInputTarget(target: InputTarget) {
      targets += target
    }

    override fun withClipRect(rect: Rect, block: () -> Unit) = block()
  }

  private fun tree(onClick: () -> Unit) =
      LayoutElement.Box(
          modifier =
              Modifier.size(100.uu).clickable(onClick = onClick).hoverBackground(Color(0xFF123456)),
          contentAlignment = Alignment.TopStart,
          children = listOf(LayoutElement.Text(Modifier, StyledText.of("inner"), TextStyle())),
      )

  @Test
  fun clickInsideBoundsInvokesCallbackAndModifierEqualityIgnoresLambda() {
    var clicks = 0
    val context = Recording()
    LayoutEngine.layout(tree { clicks++ }, context, 200, 200).draw(context)

    val target = InputDispatcher.findTopmostPressTarget(context.targets, 50, 50)
    assertTrue(target?.onPress?.invoke(50, 50, 0)?.consumed == true)
    assertEquals(1, clicks)
    assertEquals(null, InputDispatcher.findTopmostPressTarget(context.targets, 150, 150))

    assertEquals(tree {}.modifier, tree { error("other") }.modifier)
  }

  @Test
  fun hoverBackgroundOnlyDrawsUnderTheMouse() {
    val hovered = Recording(mouseX = 10, mouseY = 10)
    LayoutEngine.layout(tree {}, hovered, 200, 200).draw(hovered)
    assertTrue(Color(0xFF123456) in hovered.fills)

    val away = Recording(mouseX = 150, mouseY = 150)
    LayoutEngine.layout(tree {}, away, 200, 200).draw(away)
    assertTrue(Color(0xFF123456) !in away.fills)
  }
}
