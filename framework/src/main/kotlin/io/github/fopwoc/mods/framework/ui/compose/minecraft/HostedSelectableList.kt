package io.github.fopwoc.mods.framework.ui.compose.minecraft

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiSlot
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import org.lwjgl.opengl.GL11
import kotlin.math.max

@SideOnly(Side.CLIENT)
internal class HostedSelectableList(
    private val client: Minecraft,
    rowHeight: Int
) : GuiSlot(client, 0, 0, 0, 0, rowHeight.coerceAtLeast(12)) {
    var items: List<String> = emptyList()
    var selectedIndex: Int = -1
    var onSelectedIndexChange: (Int) -> Unit = {}
    var lastSeenEpoch: Int = -1
    var clipRect: Rect? = null

    private var dragMode: DragMode? = null
    private var dragAnchorMouseY: Int = 0
    private var dragAnchorScroll: Int = 0

    private enum class DragMode {
        LIST,
        SCROLLBAR
    }

    init {
        setShowSelectionBox(true)
    }

    fun update(bounds: Rect, items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit) {
        func_148122_a(bounds.width, bounds.height, bounds.y, bounds.y + bounds.height)
        setSlotXBoundsFromLeft(bounds.x)
        this.items = items
        this.selectedIndex = selectedIndex.coerceIn(-1, items.lastIndex)
        this.onSelectedIndexChange = onSelectedIndexChange
    }

    fun render(mouseX: Int, mouseY: Int) {
        this.mouseX = mouseX
        this.mouseY = mouseY
        bindAmountScrolledCompat()
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_FOG)
        val tessellator = Tessellator.instance
        drawContainerBackground(tessellator)
        val listLeft = left + width / 2 - getListWidth() / 2 + 2
        val listTop = top + 4 - getAmountScrolled()
        drawSelectionBox(listLeft, listTop, mouseX, mouseY)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        drawScrollbar(tessellator)
        func_148142_b(mouseX, mouseY)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glShadeModel(GL11.GL_FLAT)
        GL11.glEnable(GL11.GL_ALPHA_TEST)
        GL11.glDisable(GL11.GL_BLEND)
    }

    fun handleClick(mouseX: Int, mouseY: Int): Boolean {
        if (!contains(mouseX, mouseY)) {
            return false
        }

        dragAnchorMouseY = mouseY
        dragAnchorScroll = getAmountScrolled()
        val thumb = scrollbarThumbBounds()
        dragMode = when {
            thumb != null && thumb.contains(mouseX, mouseY) -> DragMode.SCROLLBAR
            else -> DragMode.LIST
        }

        val index = func_148124_c(mouseX, mouseY)
        if (index >= 0) {
            selectedIndex = index
            onSelectedIndexChange(index)
        }
        return true
    }

    fun handleDrag(mouseY: Int): Boolean {
        return when (dragMode) {
            DragMode.SCROLLBAR -> {
                val thumb = scrollbarThumbBounds() ?: return false
                val maxScroll = func_148135_f().coerceAtLeast(0)
                if (maxScroll <= 0) {
                    return false
                }
                val trackTop = top
                val trackHeight = (bottom - top - thumb.height).coerceAtLeast(0)
                val desiredTop = (mouseY - (dragAnchorMouseY - thumb.y)).coerceIn(trackTop, trackTop + trackHeight)
                val target = if (trackHeight == 0) 0 else (desiredTop - trackTop) * maxScroll / trackHeight
                scrollTo(target)
                true
            }
            DragMode.LIST -> {
                val delta = dragAnchorMouseY - mouseY
                scrollTo(dragAnchorScroll + delta)
                true
            }
            null -> false
        }
    }

    fun handleRelease() {
        dragMode = null
    }

    fun handleWheel(wheelDelta: Int): Boolean {
        if (wheelDelta == 0 || func_148135_f() <= 0) {
            return false
        }
        val direction = if (wheelDelta > 0) -1 else 1
        val before = getAmountScrolled()
        scrollBy(direction * slotHeight / 2)
        return getAmountScrolled() != before
    }

    override fun getSize(): Int = items.size

    override fun elementClicked(index: Int, doubleClicked: Boolean, mouseX: Int, mouseY: Int) {
        selectedIndex = index
        onSelectedIndexChange(index)
    }

    override fun isSelected(index: Int): Boolean = index == selectedIndex

    override fun drawBackground() = Unit

    override fun drawSlot(
        index: Int,
        left: Int,
        top: Int,
        height: Int,
        tessellator: Tessellator,
        mouseX: Int,
        mouseY: Int
    ) {
        val font = client.fontRenderer ?: return
        val text = items.getOrNull(index) ?: return
        val availableWidth = (getListWidth() - 10).coerceAtLeast(0)
        val trimmed = font.trimStringToWidth(text, availableWidth)
        val hovered = mouseX in left until (left + getListWidth()) && mouseY in top until (top + height)
        val color = when {
            index == selectedIndex -> 0xFFFFFF
            hovered -> 0xFFF2A8
            else -> 0xE0E0E0
        }
        val textY = top + ((height - font.FONT_HEIGHT) / 2).coerceAtLeast(0)
        font.drawStringWithShadow(trimmed, left + 2, textY, color)
    }

    override fun getListWidth(): Int = (width - 10).coerceAtLeast(32)

    override fun getScrollBarX(): Int = right - 6

    private fun contains(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom
    }

    private fun scrollTo(target: Int) {
        val delta = target.coerceAtLeast(0) - getAmountScrolled()
        if (delta != 0) {
            scrollBy(delta)
        }
    }

    private fun bindAmountScrolledCompat() {
        val maxScroll = func_148135_f().let { if (it < 0) it / 2 else it }
        val bounded = when {
            getAmountScrolled() < 0 -> 0
            getAmountScrolled() > maxScroll -> maxScroll
            else -> getAmountScrolled()
        }
        if (bounded != getAmountScrolled()) {
            scrollBy(bounded - getAmountScrolled())
        }
    }

    private fun drawScrollbar(tessellator: Tessellator) {
        val maxScroll = func_148135_f()
        if (maxScroll <= 0) {
            return
        }

        val trackLeft = getScrollBarX()
        val trackRight = trackLeft + 6
        val thumb = scrollbarThumbBounds() ?: return
        GL11.glEnable(GL11.GL_BLEND)
        OpenGlHelper.glBlendFunc(770, 771, 0, 1)
        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glShadeModel(GL11.GL_SMOOTH)
        GL11.glDisable(GL11.GL_TEXTURE_2D)

        tessellator.startDrawingQuads()
        tessellator.setColorRGBA_I(0, 255)
        tessellator.addVertexWithUV(trackLeft.toDouble(), bottom.toDouble(), 0.0, 0.0, 1.0)
        tessellator.addVertexWithUV(trackRight.toDouble(), bottom.toDouble(), 0.0, 1.0, 1.0)
        tessellator.addVertexWithUV(trackRight.toDouble(), top.toDouble(), 0.0, 1.0, 0.0)
        tessellator.addVertexWithUV(trackLeft.toDouble(), top.toDouble(), 0.0, 0.0, 0.0)
        tessellator.draw()

        tessellator.startDrawingQuads()
        tessellator.setColorRGBA_I(8421504, 255)
        tessellator.addVertexWithUV(trackLeft.toDouble(), (thumb.y + thumb.height).toDouble(), 0.0, 0.0, 1.0)
        tessellator.addVertexWithUV(trackRight.toDouble(), (thumb.y + thumb.height).toDouble(), 0.0, 1.0, 1.0)
        tessellator.addVertexWithUV(trackRight.toDouble(), thumb.y.toDouble(), 0.0, 1.0, 0.0)
        tessellator.addVertexWithUV(trackLeft.toDouble(), thumb.y.toDouble(), 0.0, 0.0, 0.0)
        tessellator.draw()

        tessellator.startDrawingQuads()
        tessellator.setColorRGBA_I(12632256, 255)
        tessellator.addVertexWithUV(trackLeft.toDouble(), (thumb.y + thumb.height - 1).toDouble(), 0.0, 0.0, 1.0)
        tessellator.addVertexWithUV((trackRight - 1).toDouble(), (thumb.y + thumb.height - 1).toDouble(), 0.0, 1.0, 1.0)
        tessellator.addVertexWithUV((trackRight - 1).toDouble(), thumb.y.toDouble(), 0.0, 1.0, 0.0)
        tessellator.addVertexWithUV(trackLeft.toDouble(), thumb.y.toDouble(), 0.0, 0.0, 0.0)
        tessellator.draw()
    }

    private fun scrollbarThumbBounds(): Rect? {
        val maxScroll = func_148135_f()
        if (maxScroll <= 0) {
            return null
        }

        val viewportHeight = (bottom - top).coerceAtLeast(1)
        val thumbHeight = max(32, viewportHeight * viewportHeight / getContentHeight().coerceAtLeast(1))
            .coerceAtMost((viewportHeight - 8).coerceAtLeast(32))
        val thumbTop = (getAmountScrolled() * (viewportHeight - thumbHeight) / maxScroll) + top
        return Rect(
            x = getScrollBarX(),
            y = thumbTop.coerceAtLeast(top),
            width = 6,
            height = thumbHeight
        )
    }
}


