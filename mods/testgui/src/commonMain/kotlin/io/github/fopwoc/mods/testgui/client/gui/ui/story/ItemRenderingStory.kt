package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.foundation.IconItem
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun ItemRenderingStory() {
    Examples {
        Example("Inventory items, including animated and tool icons.") {
            ItemSample("Diamond", "minecraft:diamond")
            ItemSample("Sword", "minecraft:diamond_sword")
            ItemSample("Compass", "minecraft:compass")
        }
        Example("Placeable blocks use their inventory appearance.") {
            ItemSample("Stone", "minecraft:stone")
            ItemSample("Chest", "minecraft:chest")
            ItemSample("Beacon", "minecraft:beacon")
        }
        Example("The same item inside different Compose bounds.") {
            Row(
                horizontalArrangement = HorizontalArrangement.spacedBy(8.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            ) {
                IconItem(ItemId("minecraft:diamond"))
                IconItem(ItemId("minecraft:diamond"), Modifier.size(24.uu))
                IconItem(ItemId("minecraft:diamond"), Modifier.size(32.uu))
                IconItem(
                    ItemId("minecraft:diamond"),
                    Modifier.size(32.uu)
                        .padding(4.uu)
                        .background(Color(0xFF202030))
                        .border(Color(0xFF8080A0))
                        .tooltip("Padded icon with a tooltip"),
                )
            }
        }
    }
}

@Composable
private fun ItemSample(label: String, id: String) {
    Row(
        horizontalArrangement = HorizontalArrangement.spacedBy(5.uu),
        verticalAlignment = VerticalAlignment.CENTER,
    ) {
        IconItem(ItemId(id))
        Text("$label · $id")
    }
}
