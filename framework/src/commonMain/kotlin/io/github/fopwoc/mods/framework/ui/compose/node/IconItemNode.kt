package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class IconItemNode(
    override var modifier: Modifier,
    var item: ItemId,
    var subpixelX: Float,
    var subpixelY: Float,
) : ComposeTreeNode(modifier)
