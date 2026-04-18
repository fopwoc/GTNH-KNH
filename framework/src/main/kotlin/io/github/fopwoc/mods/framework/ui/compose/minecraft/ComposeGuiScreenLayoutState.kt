package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode

internal class ComposeGuiScreenLayoutState {
    private var rootLayout: LayoutNode? = null
    private var cachedLayoutElement: LayoutElement? = null
    private var layoutElementDirty: Boolean = true
    private var layoutDirty: Boolean = true
    private var lastLayoutWidth: Int = -1
    private var lastLayoutHeight: Int = -1

    fun invalidateComposition() {
        layoutElementDirty = true
        layoutDirty = true
    }

    fun reset() {
        rootLayout = null
        cachedLayoutElement = null
        layoutElementDirty = true
        layoutDirty = true
        lastLayoutWidth = -1
        lastLayoutHeight = -1
    }

    fun ensureLayout(
        rootNode: RootNode,
        renderContext: RenderContext,
        width: Int,
        height: Int
    ): LayoutNode {
        if (layoutElementDirty || cachedLayoutElement == null) {
            cachedLayoutElement = rootNode.toLayoutElement()
            layoutElementDirty = false
            layoutDirty = true
        }
        if (layoutDirty || rootLayout == null || width != lastLayoutWidth || height != lastLayoutHeight) {
            rootLayout = LayoutEngine.layout(
                cachedLayoutElement ?: rootNode.toLayoutElement(),
                renderContext,
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
