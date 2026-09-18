package io.github.fopwoc.mods.framework.ui.compose.canvas

import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderLayoutState
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GpuCanvasStateTest {
    @Test
    fun submittingFrameChangesDrawnImageWithoutRecompositionOrLayout() {
        val firstFrame = GpuCanvasFrame(emptyList())
        val secondFrame = GpuCanvasFrame(emptyList())
        val canvas = GpuCanvasState(firstFrame)
        val layoutState = ComposeRenderLayoutState()
        val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
        val root = RootNode()
        val renderer = CapturingRenderContext()
        var compositions = 0

        try {
            runtime.start(root) {
                compositions++
                GpuCanvas(canvas, Modifier.width(32.uu).height(32.uu))
            }
            val layout = layoutState.ensureLayout(root, renderer, 100, 100)
            layout.draw(renderer)
            assertSame(firstFrame, renderer.drawnFrame)

            canvas.submit(secondFrame)
            runtime.pump()
            val reusedLayout = layoutState.ensureLayout(root, renderer, 100, 100)
            reusedLayout.draw(renderer)

            assertSame(layout, reusedLayout)
            assertEquals(1, compositions)
            assertSame(secondFrame, renderer.drawnFrame)
        } finally {
            runtime.dispose()
        }
    }

    private class CapturingRenderContext : RenderContext {
        var drawnFrame: GpuCanvasFrame? = null

        override val viewportWidth = 100
        override val viewportHeight = 100
        override val mouseX = 0
        override val mouseY = 0
        override val lineHeight = 9

        override fun textWidth(text: String) = text.length * 6

        override fun wrapText(text: String, maxWidth: Int) = listOf(text)

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) = Unit

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) = Unit

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) = Unit

        override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) = Unit

        override fun registerInputTarget(target: InputTarget) = Unit

        override fun withClipRect(rect: Rect, block: () -> Unit) = block()

        override fun drawGpuCanvas(bounds: Rect, frame: GpuCanvasFrame, handle: Any) {
            drawnFrame = frame
        }
    }
}
