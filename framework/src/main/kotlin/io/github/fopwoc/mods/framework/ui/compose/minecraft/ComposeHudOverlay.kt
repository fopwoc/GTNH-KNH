package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.Gui
import kotlin.math.max
import kotlin.math.min

@SideOnly(Side.CLIENT)
class ComposeHudOverlay(
    private val content: @Composable () -> Unit
) {
    private val session = ComposeHudOverlaySession(content)

    fun render(
        client: Minecraft?,
        font: FontRenderer?,
        width: Int,
        height: Int,
        mouseX: Int = -1,
        mouseY: Int = -1
    ) {
        session.render(
            client = client,
            font = font,
            width = width,
            height = height,
            mouseX = mouseX,
            mouseY = mouseY
        )
    }

    fun dispose() {
        session.dispose()
    }
}

internal class ComposeHudOverlaySession(
    private val content: @Composable () -> Unit
) {
    private val rootNode = RootNode()
    private val layoutState = ComposeGuiScreenLayoutState()
    private val composeRuntime = ComposeGuiRuntime(
        onCompositionChanged = layoutState::invalidateComposition
    )
    private val runtimeSync = ComposeGuiScreenRuntimeSync(composeRuntime)
    private val hostedWidgets = MinecraftHostedWidgetRegistry()
    private val renderedInputTargets = mutableListOf<InputTarget>()
    private var renderEpoch: Int = 0
    private var viewModelOwner: ComposeScreenViewModelOwner? = null

    fun render(
        client: Minecraft?,
        font: FontRenderer?,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val resolvedClient = client ?: return
        val resolvedFont = font ?: return
        ensureStarted()

        runtimeSync.beginFrame(System.nanoTime())
        renderEpoch += 1
        renderedInputTargets.clear()

        val frame = MinecraftRenderFrameContext(
            client = resolvedClient,
            font = resolvedFont,
            viewportWidth = width,
            viewportHeight = height,
            mouseX = mouseX,
            mouseY = mouseY,
            renderEpoch = renderEpoch
        )
        val renderContext = MinecraftRenderContext(
            frame = frame,
            hostedWidgets = hostedWidgets,
            appendInputTarget = renderedInputTargets::add,
            focusTextField = {},
            fillRectBlock = { left, top, right, bottom, color ->
                Gui.drawRect(left, top, right, bottom, color)
            },
            drawHorizontalLineBlock = { startX, endX, y, color ->
                Gui.drawRect(min(startX, endX), y, max(startX, endX) + 1, y + 1, color)
            },
            drawVerticalLineBlock = { x, startY, endY, color ->
                Gui.drawRect(x, min(startY, endY), x + 1, max(startY, endY) + 1, color)
            }
        )

        try {
            layoutState.ensureLayout(rootNode, renderContext, width, height).draw(renderContext)
        } finally {
            renderContext.resetClipState()
        }

        hostedWidgets.prune(renderEpoch)
    }

    fun dispose() {
        viewModelOwner?.clear()
        viewModelOwner = null
        composeRuntime.dispose()
        rootNode.children.clear()
        layoutState.reset()
        hostedWidgets.clear()
        renderedInputTargets.clear()
        renderEpoch = 0
    }

    private fun ensureStarted() {
        if (composeRuntime.isStarted()) {
            return
        }

        val owner = ComposeScreenViewModelOwner()
        viewModelOwner = owner
        layoutState.reset()
        owner.attachToScreen()
        composeRuntime.start(rootNode) {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalViewModelStoreOwner provides owner
            ) {
                content()
            }
        }
        owner.showOnScreen()
    }
}

