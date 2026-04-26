package io.github.fopwoc.mods.framework.ui.compose.layout.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect

internal fun drawWithinClip(context: RenderContext, clipRect: Rect, block: () -> Unit) {
    context.withClipRect(clipRect, block)
}

