package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftPrimitiveRenderCallbacks
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderSession
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.Gui
import kotlin.math.max
import kotlin.math.min

@SideOnly(Side.CLIENT)
class ComposeHudOverlay(
    content: @Composable () -> Unit
) {
    private val session = ComposeHudOverlaySession(content)

    val hasComposition: Boolean
        get() = session.hasComposition

    fun render(
        client: Minecraft?,
        font: FontRenderer?,
        width: Int,
        height: Int,
        mouseX: Int = -1,
        mouseY: Int = -1
    ) {
        if (client == null || font == null || width <= 0 || height <= 0) {
            dispose()
            return
        }

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

private class ComposeHudOverlaySession(
    content: @Composable () -> Unit
) : ComposeRenderSession(content) {
    private val renderCallbacks = object : MinecraftPrimitiveRenderCallbacks {
        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            Gui.drawRect(left, top, right, bottom, color)
        }

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) {
            Gui.drawRect(min(startX, endX), y, max(startX, endX) + 1, y + 1, color)
        }

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) {
            Gui.drawRect(x, min(startY, endY), x + 1, max(startY, endY) + 1, color)
        }
    }

    fun render(
        client: Minecraft,
        font: FontRenderer,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        advanceFrame(System.nanoTime())
        renderComposeTree(
            client = client,
            font = font,
            width = width,
            height = height,
            mouseX = mouseX,
            mouseY = mouseY,
            focusTextField = {},
            callbacks = renderCallbacks
        )
    }

    @Composable
    override fun ProvideCompositionLocals(
        owner: ComposeViewModelOwner,
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(
            LocalLifecycleOwner provides owner,
            LocalViewModelStoreOwner provides owner
        ) {
            content()
        }
    }
}

