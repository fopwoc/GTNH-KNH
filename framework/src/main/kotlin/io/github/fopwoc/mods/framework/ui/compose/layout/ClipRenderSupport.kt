package io.github.fopwoc.mods.framework.ui.compose.layout

internal fun drawWithinClip(context: RenderContext, clipRect: Rect, block: () -> Unit) {
    context.withClipRect(clipRect, block)
}

