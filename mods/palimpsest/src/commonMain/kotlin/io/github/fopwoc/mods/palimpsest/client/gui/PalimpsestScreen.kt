package io.github.fopwoc.mods.palimpsest.client.gui

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeMenuScreen
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.benchmark.BenchmarkView

class PalimpsestScreen : ComposeMenuScreen() {
    @Composable
    override fun Content() {
        BenchmarkView(screenWidth = width, screenHeight = height, onClose = ::close)
    }
}
