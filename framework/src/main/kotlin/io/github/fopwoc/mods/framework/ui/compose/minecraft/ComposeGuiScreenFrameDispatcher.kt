package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer

internal class ComposeGuiScreenFrameDispatcher(
    private val context: ComposeGuiScreenContext,
    private val layoutState: ComposeGuiScreenLayoutState,
) {
    private var renderEpoch: Int = 0

    fun reset() {
        renderEpoch = 0
        context.renderedInputTargets.clear()
    }

    fun drawScreen(
        rootNode: RootNode,
        client: Minecraft?,
        font: FontRenderer?,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        drawBackground: () -> Unit,
        drawTooltip: (lines: List<String>, x: Int, y: Int) -> Unit,
        fillRectBlock: (Int, Int, Int, Int, Int) -> Unit,
        drawHorizontalLineBlock: (Int, Int, Int, Int) -> Unit,
        drawVerticalLineBlock: (Int, Int, Int, Int) -> Unit,
        fallback: () -> Unit
    ) {
        drawBackground()

        val resolvedClient = client ?: run {
            fallback()
            return
        }
        val resolvedFont = font ?: run {
            fallback()
            return
        }

        context.runtime.pump()
        context.runtime.sendFrame(System.nanoTime())
        context.runtime.pump()
        renderEpoch += 1
        context.renderedInputTargets.clear()
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
            hostedWidgets = context.hostedWidgets,
            appendInputTarget = context.renderedInputTargets::add,
            focusTextField = context.interactionState::focusTextField,
            fillRectBlock = fillRectBlock,
            drawHorizontalLineBlock = drawHorizontalLineBlock,
            drawVerticalLineBlock = drawVerticalLineBlock
        )
        try {
            layoutState.ensureLayout(rootNode, renderContext, width, height).draw(renderContext)
        } finally {
            renderContext.resetClipState()
        }

        context.hostedWidgets.prune(renderEpoch)
        context.interactionState.refreshAfterRender()
        val hoveredTooltip = InputDispatcher.findTopmostTooltipTarget(context.renderedInputTargets, mouseX, mouseY)?.tooltipLines
        fallback()
        hoveredTooltip?.let { lines ->
            drawTooltip(lines, mouseX, mouseY)
        }
    }
}
