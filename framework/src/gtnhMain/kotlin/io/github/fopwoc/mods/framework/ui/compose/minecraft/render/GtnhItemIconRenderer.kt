package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.entity.RenderItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.lwjgl.opengl.GL11

/** Draws the game's inventory item inside a measured Compose leaf. */
internal object GtnhItemIconRenderer {
    private val renderer by lazy(LazyThreadSafetyMode.NONE) { RenderItem() }

    fun draw(client: Minecraft, bounds: Rect, item: ItemId) {
        if (bounds.isEmpty()) return
        val registered = Item.itemRegistry.getObject(item.value) as? Item ?: return
        val side = minOf(bounds.width, bounds.height)
        val scale = side / 16f
        val x = bounds.x + (bounds.width - side) / 2f
        val y = bounds.y + (bounds.height - side) / 2f

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        GL11.glPushMatrix()
        try {
            GL11.glTranslatef(x, y, 0f)
            GL11.glScalef(scale, scale, 1f)
            RenderHelper.enableGUIStandardItemLighting()
            renderer.renderItemAndEffectIntoGUI(
                client.fontRenderer,
                client.textureManager,
                ItemStack(registered),
                0,
                0,
            )
        } finally {
            GL11.glPopMatrix()
            GL11.glPopAttrib()
        }
    }
}
