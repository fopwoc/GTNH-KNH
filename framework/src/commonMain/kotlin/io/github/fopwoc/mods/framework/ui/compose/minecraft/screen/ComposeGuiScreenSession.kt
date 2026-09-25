package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderSession
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.RenderSurface
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard

/**
 * A composed screen, platform-neutral: the platform's native screen forwards its lifecycle, input
 * (falling back to its own handling when a method returns false) and drawing here.
 */
internal class ComposeGuiScreenSession(surface: RenderSurface, content: @Composable () -> Unit) :
    ComposeRenderSession(surface, content) {
    private val backDispatcher = ComposeBackDispatcher()
    private val textFields = TextFieldFocusManager()
    private val interactionState = ComposeGuiScreenInteractionState(textFields)
    private val input =
        ComposeGuiScreenInputAdapter(
            backDispatcher = backDispatcher,
            interactionState = interactionState,
            renderedInputTargets = renderedInputTargets,
            runtimeSync = runtimeSync,
        )

    fun initialize() = ensureCompositionCreated()

    fun tick(frameTimeNanos: Long) = advanceFrame(frameTimeNanos)

    override fun dispose() {
        super.dispose()
        interactionState.reset()
    }

    fun keyPressed(press: KeyPress, clipboard: TextClipboard): Boolean =
        input.keyPressed(press, clipboard)

    fun charTyped(char: Char): Boolean = input.charTyped(char)

    fun mouseScrolled(mouseX: Int, mouseY: Int, wheelDelta: Int): Boolean =
        input.mouseScrolled(mouseX, mouseY, wheelDelta)

    fun mousePressed(mouseX: Int, mouseY: Int, button: Int): Boolean =
        input.mousePressed(mouseX, mouseY, button)

    fun mouseDragged(mouseX: Int, mouseY: Int, button: Int): Boolean =
        input.mouseDragged(mouseX, mouseY, button)

    fun mouseReleased(mouseX: Int, mouseY: Int, button: Int): Boolean =
        input.mouseReleased(mouseX, mouseY, button)

    fun mouseMoved() = input.mouseMoved()

    /** Draws the tree; returns the tooltip lines under the mouse for the platform to draw last. */
    fun render(width: Int, height: Int, mouseX: Int, mouseY: Int): List<String>? {
        textFields.beginFrame()
        renderComposeTree(width, height, mouseX, mouseY, textFields)
        textFields.endFrame()
        interactionState.refreshAfterRender()
        return InputDispatcher.findTopmostTooltipTarget(renderedInputTargets, mouseX, mouseY)
            ?.tooltipLines
    }

    @Composable
    override fun ProvideCompositionLocals(
        owner: ComposeViewModelOwner,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalBackDispatcher provides backDispatcher,
            LocalLifecycleOwner provides owner,
            LocalViewModelStoreOwner provides owner,
        ) {
            content()
        }
    }

    override fun onCompositionReused(owner: ComposeViewModelOwner) {
        owner.onResume()
    }
}
