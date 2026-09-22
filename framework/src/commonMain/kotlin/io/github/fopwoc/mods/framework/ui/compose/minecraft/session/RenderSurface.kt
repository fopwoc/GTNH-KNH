package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost

/**
 * Where a Compose session draws: the platform's GUI drawing for one screen or HUD layer. It owns
 * per-session platform resources (GPU canvas textures, text caches) across frames.
 */
internal interface RenderSurface {
    /** Starts a frame; input targets drawn in it are appended to [inputTargets]. */
    fun beginFrame(
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        inputTargets: MutableList<InputTarget>,
        textFields: TextFieldHost,
    ): RenderContext

    fun endFrame()

    fun dispose()
}
