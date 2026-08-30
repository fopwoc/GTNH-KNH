package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MinecraftClipScissorTest {
  @Test
  fun mergeClipRectsReturnsOtherWhenEitherSideIsNull() {
    val rect = Rect(4, 5, 6, 7)

    assertEquals(rect, mergeClipRects(null, rect))
    assertEquals(rect, mergeClipRects(rect, null))
    assertNull(mergeClipRects(null, null))
  }

  @Test
  fun mergeClipRectsIntersectsOverlappingRects() {
    val first = Rect(2, 3, 8, 8)
    val second = Rect(6, 1, 8, 8)

    assertEquals(Rect(6, 3, 4, 6), mergeClipRects(first, second))
  }

  @Test
  fun mergeClipRectsKeepsEmptyIntersectionForNonOverlappingRects() {
    val first = Rect(2, 3, 4, 4)
    val second = Rect(10, 3, 4, 4)

    assertEquals(Rect(10, 3, 0, 4), mergeClipRects(first, second))
  }

  @Test
  fun toMinecraftScissorRectFlipsYAxisAndScalesToDisplayBounds() {
    val rect = Rect(10, 20, 30, 40)

    val scissor =
        rect.toMinecraftScissorRect(
            displayWidth = 200,
            displayHeight = 100,
            viewportWidth = 100,
            viewportHeight = 100,
        )

    assertEquals(Rect(x = 20, y = 40, width = 60, height = 40), scissor)
  }

  @Test
  fun toMinecraftScissorRectClampsIntoDisplayBounds() {
    val rect = Rect(-5, 80, 30, 30)

    val scissor =
        rect.toMinecraftScissorRect(
            displayWidth = 100,
            displayHeight = 100,
            viewportWidth = 100,
            viewportHeight = 100,
        )

    assertEquals(Rect(x = 0, y = 0, width = 25, height = 20), scissor)
  }

  @Test
  fun toMinecraftScissorRectUsesExactScaledGuiProjectionForCeiledViewportSizes() {
    val rect = Rect(333, 20, 1, 30)

    val scissor =
        rect.toMinecraftScissorRect(
            MinecraftGuiProjection(
                displayWidth = 1000,
                displayHeight = 800,
                scaledWidth = 1000.0 / 3.0,
                scaledHeight = 800.0 / 3.0,
                scaleFactor = 3,
            )
        )

    assertEquals(Rect(x = 999, y = 650, width = 1, height = 91), scissor)
  }

  @Test
  fun toMinecraftScissorRectUsesActiveViewportOffsetForLiveProjection() {
    val rect = Rect(280, 10, 20, 30)

    val scissor =
        rect.toMinecraftScissorRect(
            MinecraftGuiProjection(
                displayWidth = 1000,
                displayHeight = 800,
                scaledWidth = 300.0,
                scaledHeight = 240.0,
                viewportX = 50,
                viewportY = 20,
                viewportWidth = 900,
                viewportHeight = 720,
                scaleFactor = 3,
            )
        )

    assertEquals(Rect(x = 890, y = 620, width = 60, height = 90), scissor)
  }

  @Test
  fun toMinecraftScissorRectReturnsEmptyRectWhenDimensionsAreNonPositive() {
    val rect = Rect(10, 20, 30, 40)

    assertEquals(
        Rect(0, 0, 0, 0),
        rect.toMinecraftScissorRect(
            displayWidth = 0,
            displayHeight = 100,
            viewportWidth = 100,
            viewportHeight = 100,
        ),
    )
    assertEquals(
        Rect(0, 0, 0, 0),
        rect.toMinecraftScissorRect(
            displayWidth = 100,
            displayHeight = 100,
            viewportWidth = 0,
            viewportHeight = 100,
        ),
    )
  }
}
