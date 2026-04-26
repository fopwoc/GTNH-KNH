package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class RootNode : ComposeTreeNode(ROOT_MODIFIER) {

    private companion object {
        val ROOT_MODIFIER: Modifier = Modifier.fillMaxSize()
    }
}

