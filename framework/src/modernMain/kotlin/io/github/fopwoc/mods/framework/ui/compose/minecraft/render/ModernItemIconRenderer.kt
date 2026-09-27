package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.minecraft.Identifier
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

/** Uses the installed Minecraft version's GUI item renderer and its resource-pack models. */
internal object ModernItemIconRenderer {
    fun draw(graphics: GuiDrawing, bounds: Rect, item: ItemId) {
        if (bounds.isEmpty()) return
        val identifier = Identifier.tryParse(item.value) ?: return
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) return
        /*? if >=26 {*/
        val stack = ItemStack(BuiltInRegistries.ITEM.get(identifier).orElseThrow(), 1)
        /*?} else {*/
        /*val stack = ItemStack(BuiltInRegistries.ITEM.get(identifier), 1)
         */
        /*?}*/
        val side = minOf(bounds.width, bounds.height)
        val scale = side / 16f
        val x = bounds.x + (bounds.width - side) / 2f
        val y = bounds.y + (bounds.height - side) / 2f
        val pose = graphics.pose()
        /*? if >=26 {*/
        pose.pushMatrix()
        try {
            pose.translate(x, y)
            pose.scale(scale, scale)
            graphics.item(stack, 0, 0)
        } finally {
            pose.popMatrix()
        }
        /*?} else {*/
        /*pose.pushPose()
        try {
            pose.translate(x, y, 0f)
            pose.scale(scale, scale, 1f)
            graphics.renderItem(stack, 0, 0)
        } finally {
            pose.popPose()
        }
         */
        /*?}*/
    }
}
