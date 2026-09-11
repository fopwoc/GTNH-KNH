package io.github.fopwoc.mods.testgui.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.BoxScope
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeHudOverlay
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent

/**
 * Showcase for [ComposeHudOverlay]: a tick-driven readout, a frame-clock animation and the four
 * anchor corners. Toggle with `/testgui hud`.
 */
@SideOnly(Side.CLIENT)
object TestGuiHudOverlay {
  private const val BOX_WIDTH = 150
  private const val BOX_HEIGHT = 46

  var enabled: Boolean = false
    private set

  private val host = ComposeHudOverlay { Content(model) }
  private var model by mutableStateOf(HudModel())

  fun toggle(): Boolean {
    enabled = !enabled
    if (!enabled) {
      host.dispose()
    }
    return enabled
  }

  @SubscribeEvent
  fun onRender(event: RenderGameOverlayEvent.Post) {
    if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR || !enabled) {
      return
    }
    val minecraft = Minecraft.getMinecraft()
    val player = minecraft.thePlayer ?: return
    model =
        HudModel(
            screenWidth = event.resolution.scaledWidth,
            screenHeight = event.resolution.scaledHeight,
            position = "%.1f / %.1f / %.1f".format(player.posX, player.posY, player.posZ),
            fps = Minecraft.debugFPS,
        )
    host.render(
        client = minecraft,
        font = minecraft.fontRenderer,
        width = event.resolution.scaledWidth,
        height = event.resolution.scaledHeight,
    )
  }

  private data class HudModel(
      val screenWidth: Int = 0,
      val screenHeight: Int = 0,
      val position: String = "",
      val fps: Int = 0,
  )

  @Composable
  private fun Content(model: HudModel) {
    // Frame-clock animation: advances once per rendered frame, no ticks involved.
    var frames by mutableIntStateOf(0)
    LaunchedEffect(Unit) {
      while (true) {
        withFrameNanos { frames++ }
      }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      corner(model, Alignment.TopStart, "top-start") {
        Text("FPS ${model.fps} · frames $frames")
        Text(model.position, style = TextStyle(color = Color(0xFFB8D7FF)))
      }
      corner(model, Alignment.TopEnd, "top-end") {
        ProgressBar(fraction = (frames % 120) / 120f)
      }
      corner(model, Alignment.BottomStart, "bottom-start") {
        Text(
            styledText {
              withColor(MinecraftColor.Gold) { withBold { +"Bold gold" } }
              +" and "
              withItalic { +"italic" }
            }
        )
      }
    }
  }

  @Composable
  private fun BoxScope.corner(
      model: HudModel,
      alignment: Alignment,
      title: String,
      content: @Composable () -> Unit,
  ) {
    HudAnchor(
        bounds =
            HudRect(
                left = 4,
                top = 4,
                width = model.screenWidth - 8,
                height = model.screenHeight - 30,
            ),
        contentAlignment = alignment,
    ) {
      Column(
          modifier =
              Modifier.width(BOX_WIDTH.uu)
                  .height(BOX_HEIGHT.uu)
                  .background(Color(0xA0101018))
                  .border(Color(0xFF4A4A56))
                  .padding(4.uu),
          verticalArrangement = VerticalArrangement.spacedBy(3.uu),
      ) {
        Text(title, style = TextStyle(color = Color(0xFFFFD54A)))
        content()
      }
    }
  }

  @Composable
  private fun ProgressBar(fraction: Float) {
    Row(modifier = Modifier.fillMaxWidth().height(6.uu).background(Color(0xFF202028))) {
      Box(
          modifier =
              Modifier.weight(fraction.coerceAtLeast(0.001f))
                  .fillMaxHeight()
                  .background(Color(0xFF55FF55))
      )
      Spacer(modifier = Modifier.weight((1f - fraction).coerceAtLeast(0.001f)))
    }
  }
}
