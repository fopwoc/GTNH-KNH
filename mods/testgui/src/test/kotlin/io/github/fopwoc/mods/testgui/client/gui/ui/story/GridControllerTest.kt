package io.github.fopwoc.mods.testgui.client.gui.ui.story

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridControllerTest {
  @Test
  fun `panning changes the selected cells and their canvas positions`() {
    val controller = GridController()
    val images = NoiseImages()
    val initial = controller.frame(images, 64, 64)

    controller.centerX = 8.0
    val moved = controller.frame(images, 64, 64)

    assertEquals(4, initial.draws.size)
    assertEquals(4, moved.draws.size)
    assertTrue(initial.draws.first().image === moved.draws.first().image)
    assertEquals(initial.draws.first().x - 16f, moved.draws.first().x)
  }

  @Test
  fun `zooming out selects a coarser level and keeps visible draw count bounded`() {
    val controller = GridController()
    val images = NoiseImages()
    val close = controller.frame(images, 300, 150)

    controller.zoom = 0.125
    val far = controller.frame(images, 300, 150)

    assertEquals(3, controller.level)
    assertTrue(far.draws.size < 100)
    assertTrue(close.draws.first().image !== far.draws.first().image)
  }
}
