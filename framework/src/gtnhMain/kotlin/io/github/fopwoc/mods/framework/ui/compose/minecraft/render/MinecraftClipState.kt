package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.render.ClipStack
import org.lwjgl.opengl.GL11

/** [ClipStack] applied as a GL scissor in the 1.7.10 framebuffer's coordinates. */
internal fun minecraftClipState(
    frame: MinecraftRenderFrameContext,
    appendInputTarget: (InputTarget) -> Unit,
): ClipStack {
    // One GL viewport/ScaledResolution query per frame instead of one per clip change.
    val projection by lazy { resolveMinecraftGuiProjection(frame.client) }
    return ClipStack(frame.viewportWidth, frame.viewportHeight, appendInputTarget) { rect ->
        when {
            rect == null -> GL11.glDisable(GL11.GL_SCISSOR_TEST)
            rect.isEmpty() -> {
                GL11.glEnable(GL11.GL_SCISSOR_TEST)
                GL11.glScissor(0, 0, 0, 0)
            }
            else -> {
                val scissorRect = rect.toMinecraftScissorRect(projection)
                GL11.glEnable(GL11.GL_SCISSOR_TEST)
                GL11.glScissor(scissorRect.x, scissorRect.y, scissorRect.width, scissorRect.height)
            }
        }
    }
}
