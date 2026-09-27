package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.foundation.IconItem
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderLayoutState
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IconItemTest {
    @Test
    fun drawsAtInventorySizeAndRefreshesItemWithoutRelayout() {
        val layoutState = ComposeRenderLayoutState()
        val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
        val root = RootNode()
        val renderer = RecordingRenderContext()
        var item by mutableStateOf(ItemId("minecraft:stone"))

        try {
            runtime.start(root) { IconItem(item) }
            val layout = layoutState.ensureLayout(root, renderer, 100, 100)
            layout.draw(renderer)
            assertEquals(Rect(0, 0, 16, 16) to ItemId("minecraft:stone"), renderer.drawn)

            item = ItemId("minecraft:diamond")
            runtime.pump()
            runtime.sendFrame(System.nanoTime())
            runtime.pump()
            val refreshed = layoutState.ensureLayout(root, renderer, 100, 100)
            refreshed.draw(renderer)
            assertSame(layout, refreshed)
            assertEquals(Rect(0, 0, 16, 16) to ItemId("minecraft:diamond"), renderer.drawn)
        } finally {
            runtime.dispose()
        }
    }

    private class RecordingRenderContext : RenderContext {
        var drawn: Pair<Rect, ItemId>? = null

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

        override fun drawItemIcon(bounds: Rect, item: ItemId) {
            drawn = bounds to item
        }
    }
}
