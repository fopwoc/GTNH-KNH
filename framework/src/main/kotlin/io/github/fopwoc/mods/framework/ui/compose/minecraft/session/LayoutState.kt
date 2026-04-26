package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode

internal class ComposeRenderLayoutState {
    private var rootLayout: LayoutNode? = null
    private var layoutElementDirty: Boolean = true
    private var layoutDirty: Boolean = true
    private var lastLayoutWidth: Int = -1
    private var lastLayoutHeight: Int = -1

    fun invalidateComposition() {
        layoutElementDirty = true
    }

    fun reset() {
        rootLayout = null
        layoutElementDirty = true
        layoutDirty = true
        lastLayoutWidth = -1
        lastLayoutHeight = -1
    }

    fun ensureLayout(
        rootNode: RootNode,
        textMetrics: TextMetrics,
        width: Int,
        height: Int
    ): LayoutNode {
        if (layoutElementDirty) {
            val existingLayout = rootLayout
            if (existingLayout == null || !existingLayout.isLayoutEquivalentTo(rootNode)) {
                layoutDirty = true
            } else {
                existingLayout.updateFromNode(rootNode)
                rootLayout = LayoutEngine.refreshPlacement(existingLayout)
            }
            layoutElementDirty = false
        }
        if (layoutDirty || rootLayout == null || width != lastLayoutWidth || height != lastLayoutHeight) {
            rootLayout = LayoutEngine.layout(
                rootNode,
                textMetrics,
                width,
                height
            )
            layoutDirty = false
            lastLayoutWidth = width
            lastLayoutHeight = height
        }
        return rootLayout ?: error("Layout should be available after ensureLayout")
    }
}


