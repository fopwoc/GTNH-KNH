package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import cpw.mods.fml.client.config.GuiUtils
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.layout.render.WidgetSlice
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiScreen
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

internal class MinecraftRenderContext(
    private val frame: MinecraftRenderFrameContext,
    appendInputTarget: (InputTarget) -> Unit,
    callbacks: MinecraftPrimitiveRenderCallbacks,
    wrapCache: TextWrapCache = TextWrapCache(),
    override val textFields: TextFieldHost = TextFieldHost.None,
) : RenderContext {
  override val viewportWidth: Int
    get() = frame.viewportWidth

  override val viewportHeight: Int
    get() = frame.viewportHeight

  override val mouseX: Int
    get() = frame.mouseX

  override val mouseY: Int
    get() = frame.mouseY

  private val textMetrics = MinecraftFontTextMetrics(frame.font, wrapCache)
  private val primitiveDrawer =
      MinecraftPrimitiveDrawer(
          font = frame.font,
          callbacks = callbacks,
      )
  private val clipState =
      MinecraftClipState(
          frame = frame,
          appendInputTarget = appendInputTarget,
      )

  override val lineHeight: Int
    get() = textMetrics.lineHeight

  override fun textWidth(text: String): Int = textMetrics.textWidth(text)

  override fun wrapText(text: String, maxWidth: Int): List<String> =
      textMetrics.wrapText(text, maxWidth)

  override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) {
    primitiveDrawer.fillRect(left, top, right, bottom, color)
  }

  override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) {
    primitiveDrawer.drawHorizontalLine(startX, endX, y, color)
  }

  override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) {
    primitiveDrawer.drawVerticalLine(x, startY, endY, color)
  }

  override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) {
    primitiveDrawer.drawText(text, x, y, color, shadow)
  }

  override fun registerInputTarget(target: InputTarget) {
    clipState.registerInputTarget(target)
  }

  override fun withClipRect(rect: Rect, block: () -> Unit) {
    clipState.withClipRect(rect, block)
  }

  override fun drawWidgetSlice(slice: WidgetSlice, x: Int, y: Int, width: Int, height: Int) {
    GL11.glColor4f(1f, 1f, 1f, 1f)
    GuiUtils.drawContinuousTexturedBox(
        WIDGETS_TEXTURE,
        x,
        y,
        slice.u,
        slice.v,
        width,
        height,
        slice.width,
        slice.height,
        slice.top,
        slice.bottom,
        slice.left,
        slice.right,
        0f,
    )
  }

  override fun drawWidgetSprite(u: Int, v: Int, width: Int, height: Int, x: Int, y: Int) {
    GL11.glColor4f(1f, 1f, 1f, 1f)
    frame.client.textureManager.bindTexture(WIDGETS_TEXTURE)
    spriteGui.drawTexturedModalRect(x, y, u, v, width, height)
  }

  override fun playClickSound() {
    frame.client.soundHandler.playSound(
        PositionedSoundRecord.func_147674_a(ResourceLocation("gui.button.press"), 1.0f)
    )
  }

  override fun keyModifiers(): KeyModifiers =
      KeyModifiers(ctrl = GuiScreen.isCtrlKeyDown(), shift = GuiScreen.isShiftKeyDown())

  fun resetClipState() {
    clipState.reset()
  }
}

private val WIDGETS_TEXTURE = ResourceLocation("textures/gui/widgets.png")
private val spriteGui = Gui()
