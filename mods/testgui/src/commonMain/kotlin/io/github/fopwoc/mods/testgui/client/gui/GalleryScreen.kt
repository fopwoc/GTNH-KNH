package io.github.fopwoc.mods.testgui.client.gui

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeMenuScreen
import io.github.fopwoc.mods.testgui.client.gui.ui.page.gallery.GalleryView

class GalleryScreen : ComposeMenuScreen() {
    @Composable
    override fun Content() {
        GalleryView(screenWidth = width, screenHeight = height, onClose = ::close)
    }
}
