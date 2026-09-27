package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.IconItemNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier

/** Draws the inventory appearance of a registered item inside a Compose layout. */
@Composable
fun IconItem(item: ItemId, modifier: Modifier = Modifier) {
    ComposeNode<IconItemNode, NodeApplier>(
        factory = { IconItemNode(modifier, item) },
        update = {
            set(modifier) { this.modifier = it }
            set(item) { this.item = it }
        },
    )
}
