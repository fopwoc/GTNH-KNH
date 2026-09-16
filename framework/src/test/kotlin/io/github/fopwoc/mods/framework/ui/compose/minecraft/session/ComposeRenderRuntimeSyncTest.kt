package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeRenderRuntimeSyncTest {
  @Test
  fun renderAdvancesComposeFrameClockWithoutGameTicks() {
    val runtime = ComposeGuiRuntime(onCompositionChanged = {})
    val sync = ComposeRenderRuntimeSync(runtime)
    var frames by mutableIntStateOf(0)
    try {
      runtime.start(RootNode()) {
        LaunchedEffect(Unit) {
          while (true) {
            withFrameNanos { frames++ }
          }
        }
      }

      sync.syncBeforeRender()
      sync.syncBeforeRender()

      assertEquals(2, frames)
    } finally {
      runtime.dispose()
    }
  }

  @Test
  fun gameTickAndRenderShareOneComposeFrame() {
    val runtime = ComposeGuiRuntime(onCompositionChanged = {})
    val sync = ComposeRenderRuntimeSync(runtime)
    var frames by mutableIntStateOf(0)
    try {
      runtime.start(RootNode()) {
        LaunchedEffect(Unit) {
          while (true) {
            withFrameNanos { frames++ }
          }
        }
      }

      sync.updateScreen(System.nanoTime())
      sync.syncBeforeRender()

      assertEquals(1, frames)
    } finally {
      runtime.dispose()
    }
  }
}
