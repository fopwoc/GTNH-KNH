package io.github.fopwoc.mods.framework.ui.compose.hud

import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeHudSession
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.RenderSurface

/** Drives one [HudLayer] for a platform: composes it while visible and releases it when hidden. */
internal class HudLayerHost(private val layer: HudLayer, private val newSurface: () -> RenderSurface) {
    private var session: ComposeHudSession? = null

    fun render(width: Int, height: Int) {
        if (!layer.visible || width <= 0 || height <= 0) {
            dispose()
            return
        }
        layer.width = width
        layer.height = height
        layer.beforeFrame()
        val active = session ?: ComposeHudSession(newSurface()) { layer.Content() }.also { session = it }
        active.render(width, height)
    }

    fun dispose() {
        session?.dispose()
        session = null
    }
}
