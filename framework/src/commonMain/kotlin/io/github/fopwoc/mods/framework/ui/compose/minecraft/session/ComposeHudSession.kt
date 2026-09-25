package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner

/** A composed HUD layer: no input, drawn every frame over the game. */
internal class ComposeHudSession(surface: RenderSurface, content: @Composable () -> Unit) :
    ComposeRenderSession(surface, content) {

    fun render(width: Int, height: Int, mouseX: Int = -1, mouseY: Int = -1) {
        advanceFrame(System.nanoTime())
        renderComposeTree(width, height, mouseX, mouseY, TextFieldHost.None)
    }

    @Composable
    override fun ProvideCompositionLocals(
        owner: ComposeViewModelOwner,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalLifecycleOwner provides owner,
            LocalViewModelStoreOwner provides owner,
        ) {
            content()
        }
    }
}
