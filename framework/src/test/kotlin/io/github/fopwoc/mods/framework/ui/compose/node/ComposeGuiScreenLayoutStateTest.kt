package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreenLayoutState
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutCacheRefreshTest {
    @Test
    fun hostedButtonEqualityIgnoresCallbackIdentity() {
        val first = LayoutElement.Button(
            modifier = Modifier.fillMaxWidth(),
            hostKey = Any(),
            text = StyledText.of("Apply"),
            enabled = true,
            onClick = {}
        )
        val second = LayoutElement.Button(
            modifier = Modifier.fillMaxWidth(),
            hostKey = first.hostKey,
            text = StyledText.of("Apply"),
            enabled = true,
            onClick = { error("different callback instance") }
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun callbackOnlyRecompositionRefreshesHostedBindingsWithoutRebuildingLayout() {
        val layoutState = ComposeGuiScreenLayoutState()
        val root = RootNode()
        val buttonNode = ButtonNode(
            modifier = Modifier.fillMaxWidth(),
            text = StyledText.of("Run"),
            enabled = true,
            onClick = {}
        )
        root.children += buttonNode

        val firstClicks = mutableListOf<String>()
        buttonNode.onClick = { firstClicks += "first" }

        val renderContext = RecordingRenderContext()
        val firstLayout = layoutState.ensureLayout(root, renderContext, width = 220, height = 80)
        firstLayout.draw(renderContext)
        renderContext.lastButtonClick?.invoke()
        assertEquals(listOf("first"), firstClicks)

        val secondClicks = mutableListOf<String>()
        buttonNode.onClick = { secondClicks += "second" }
        layoutState.invalidateComposition()

        val refreshedContext = RecordingRenderContext()
        val secondLayout = layoutState.ensureLayout(root, refreshedContext, width = 220, height = 80)
        secondLayout.draw(refreshedContext)
        refreshedContext.lastButtonClick?.invoke()

        assertSame(firstLayout, secondLayout)
        assertEquals(listOf("second"), secondClicks)
    }

    @Test
    fun scrollStateChangesStillForceRelayoutWhenPlacementChanges() {
        val scrollState = ScrollState()
        val layoutState = ComposeGuiScreenLayoutState()
        val root = RootNode()
        val scrollableColumn = ColumnNode(
            modifier = Modifier.fillMaxWidth().height(40.uu).verticalScroll(scrollState),
            verticalArrangement = VerticalArrangement.spacedBy(0.uu),
            horizontalAlignment = HorizontalAlignment.START
        )
        scrollableColumn.children += SpacerNode(modifier = Modifier.fillMaxWidth().height(30.uu))
        scrollableColumn.children += SpacerNode(modifier = Modifier.fillMaxWidth().height(30.uu))
        root.children += scrollableColumn

        val renderContext = RecordingRenderContext()
        val firstLayout = layoutState.ensureLayout(root, renderContext, width = 120, height = 40)
        val firstChildY = firstLayout.children.single().children.first().bounds.y
        assertTrue(scrollState.maxValue > 0)

        scrollState.scrollTo(10)
        layoutState.invalidateComposition()

        val secondLayout = layoutState.ensureLayout(root, renderContext, width = 120, height = 40)
        val secondChildY = secondLayout.children.single().children.first().bounds.y

        assertNotSame(firstLayout, secondLayout)
        assertNotEquals(firstChildY, secondChildY)
    }

    private class RecordingRenderContext : RenderContext {
        override val viewportWidth: Int = 320
        override val viewportHeight: Int = 180
        override val mouseX: Int = 0
        override val mouseY: Int = 0
        override val lineHeight: Int = 9

        var lastButtonClick: (() -> Unit)? = null

        override fun textWidth(text: String): Int = text.length * 6

        override fun wrapText(text: String, maxWidth: Int): List<String> = listOf(text)

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) = Unit

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) = Unit

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) = Unit

        override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) = Unit

        override fun drawVanillaButton(
            bounds: Rect,
            hostKey: Any,
            text: String,
            enabled: Boolean,
            onClick: () -> Unit
        ) {
            lastButtonClick = onClick
        }

        override fun drawVanillaCheckbox(
            bounds: Rect,
            hostKey: Any,
            label: String,
            checked: Boolean,
            enabled: Boolean,
            onCheckedChange: (Boolean) -> Unit
        ) = Unit

        override fun drawVanillaTextField(
            bounds: Rect,
            hostKey: Any,
            state: TextFieldState,
            placeholder: String,
            enabled: Boolean,
            style: TextFieldStyle
        ) = Unit

        override fun drawVanillaSlider(
            bounds: Rect,
            hostKey: Any,
            value: Double,
            valueRangeStart: Double,
            valueRangeEnd: Double,
            label: String,
            suffix: String,
            enabled: Boolean,
            showDecimal: Boolean,
            onValueChange: (Double) -> Unit
        ) = Unit

        override fun drawVanillaSelectableList(
            bounds: Rect,
            hostKey: Any,
            items: List<String>,
            selectedIndex: Int,
            rowHeight: Int,
            onSelectedIndexChange: (Int) -> Unit
        ) = Unit

        override fun registerInputTarget(target: InputTarget) = Unit

        override fun withClipRect(rect: Rect, block: () -> Unit) {
            block()
        }
    }
}


