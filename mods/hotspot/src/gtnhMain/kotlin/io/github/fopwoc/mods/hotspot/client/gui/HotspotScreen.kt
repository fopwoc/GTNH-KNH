package io.github.fopwoc.mods.hotspot.client.gui

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeMenuScreen
import io.github.fopwoc.mods.hotspot.client.HotspotKeyBindings
import io.github.fopwoc.mods.hotspot.client.gui.ui.Entrypoint
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.client.profile.TileEntityRef

class HotspotScreen : ComposeMenuScreen(toggleKey = HotspotKeyBindings.openMenu) {
    override fun onUnhandledKey(press: KeyPress): Boolean {
        if (super.onUnhandledKey(press)) {
            return true
        }
        // Cmd/Ctrl+A picks every listed tile entity of the focused chunk.
        if (press.key == Key.A && press.modifiers.ctrl) {
            val chunk = ProfileStore.focusedChunk ?: return true
            val listed = ProfileStore.chunk(chunk)?.tileEntities.orEmpty()
            ProfileStore.setSelectedInChunk(
                chunk,
                listed.map { TileEntityRef(chunk.dimensionId, it.x, it.y, it.z) },
            )
            refreshNow()
            return true
        }
        return false
    }

    @Composable
    override fun Content() {
        Entrypoint(
            screenWidth = width,
            screenHeight = height,
            refreshToken = refreshToken,
            onClose = ::close,
        )
    }
}
