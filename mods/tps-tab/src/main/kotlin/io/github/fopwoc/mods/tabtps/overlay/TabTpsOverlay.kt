package io.github.fopwoc.mods.tabtps.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeHudOverlay
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.monitor.TabTpsMonitor
import io.github.fopwoc.mods.tabtps.protocol.TpsMetrics
import java.util.Locale
import kotlin.math.max
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent

@SideOnly(Side.CLIENT)
object TabTpsOverlay {
  private const val BOX_PADDING = 4
  private const val BOX_SPACING = 3

  private val overlayHost = ComposeHudOverlay { OverlayContent(overlayState) }

  private var overlayState by mutableStateOf(OverlayState())

  @SubscribeEvent
  fun onRender(event: RenderGameOverlayEvent.Post) {
    if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
      return
    }

    val snapshot = TabTpsMonitor.snapshot()
    if (!TabTpsConfig.enabled || !snapshot.tabOpen) {
      hideOverlay()
      return
    }

    val minecraft = Minecraft.getMinecraft()
    val fontRenderer = minecraft.fontRenderer ?: return hideOverlay()
    val tabBounds =
        computeTabBounds(minecraft, event.resolution.scaledWidth) ?: return hideOverlay()
    val lines = buildLines(snapshot)
    if (lines.isEmpty()) {
      hideOverlay()
      return
    }

    val boxWidth = lines.maxOf { fontRenderer.getStringWidth(it.text) } + BOX_PADDING * 2
    val boxHeight = lines.size * (fontRenderer.FONT_HEIGHT + 1) + BOX_PADDING * 2 - 1
    overlayState =
        OverlayState(
            anchorBounds = tabBounds,
            width = boxWidth,
            height = boxHeight,
            lines = lines,
        )
    overlayHost.render(
        client = minecraft,
        font = fontRenderer,
        width = event.resolution.scaledWidth,
        height = event.resolution.scaledHeight,
    )
  }

  @SubscribeEvent
  fun onClientDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
    hideOverlay()
  }

  private fun buildLines(snapshot: TabTpsMonitor.Snapshot): List<OverlayLine> {
    val measurement = snapshot.measurement
    if (measurement == null) {
      val status = snapshot.statusMessage
      return if (TabTpsConfig.showPlaceholder && status != null) {
        listOf(OverlayLine(status, NEUTRAL_COLOR))
      } else {
        emptyList()
      }
    }

    val stale = snapshot.tickNow - measurement.receivedAtTick > TabTpsConfig.staleDataTicks
    val response = measurement.snapshot
    return buildList {
      add(metricLine("Server", response.server, stale))
      response.dimensions.forEach { dimension ->
        val currentPrefix =
            if (dimension.dimensionId == response.currentDimensionId) "Current — " else ""
        val label = "$currentPrefix${dimension.dimensionName} (#${dimension.dimensionId})"
        add(metricLine(label, dimension.metrics, stale))
      }
    }
  }

  private fun metricLine(label: String, metrics: TpsMetrics, stale: Boolean): OverlayLine {
    val staleSuffix = if (stale) " (stale)" else ""
    val text =
        String.format(
            Locale.ROOT,
            "%s: %.2f TPS · %.2f ms/t%s",
            label,
            metrics.tps,
            metrics.mspt,
            staleSuffix,
        )
    return OverlayLine(text, colorFor(metrics, stale))
  }

  private fun colorFor(metrics: TpsMetrics, stale: Boolean): Color {
    if (stale) {
      return STALE_COLOR
    }

    return when {
      metrics.tps >= 19.5 -> HEALTHY_COLOR
      metrics.tps >= 18.0 -> DEGRADED_COLOR
      else -> STRUGGLING_COLOR
    }
  }

  private fun computeTabBounds(minecraft: Minecraft, screenWidth: Int): HudRect? {
    val player = minecraft.thePlayer ?: return null
    val world = minecraft.theWorld ?: return null
    val handler = player.sendQueue ?: return null
    val scoreObjective = world.scoreboard.func_96539_a(0)
    val playerCount = handler.playerInfoList.size

    if (
        !minecraft.gameSettings.keyBindPlayerList.getIsKeyPressed() ||
            (minecraft.isIntegratedServerRunning() && playerCount <= 1 && scoreObjective == null)
    ) {
      return null
    }

    val maxPlayers = max(1, max(handler.currentServerMaxPlayers, playerCount))
    var rows = maxPlayers
    var columns = 1
    while (rows > 20) {
      columns++
      rows = (maxPlayers + columns - 1) / columns
    }

    val columnWidth = minOf(150, 300 / columns)
    val left = (screenWidth - columns * columnWidth) / 2
    return HudRect(
        left = left - 1,
        top = 9,
        width = columns * columnWidth + 1,
        height = rows * 9 + 1,
    )
  }

  private fun hideOverlay() {
    overlayState = OverlayState()
    overlayHost.dispose()
  }

  @Composable
  private fun OverlayContent(state: OverlayState) {
    if (state.lines.isEmpty()) {
      return
    }

    Box(modifier = Modifier.fillMaxSize()) {
      HudAnchor(bounds = state.anchorBounds, contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier =
                Modifier.width(state.width.uu)
                    .height(state.height.uu)
                    .offset(y = BOX_SPACING.uu)
                    .background(Color(0x78000000))
        ) {
          Column(
              modifier = Modifier.fillMaxSize().padding(BOX_PADDING.uu),
              verticalArrangement = VerticalArrangement.spacedBy(1.uu),
          ) {
            state.lines.forEach { line ->
              Text(
                  text = line.text,
                  modifier = Modifier.fillMaxWidth(),
                  style = TextStyle(color = line.color),
              )
            }
          }
        }
      }
    }
  }

  private data class OverlayLine(
      val text: String,
      val color: Color,
  )

  private data class OverlayState(
      val anchorBounds: HudRect = HudRect.Zero,
      val width: Int = 0,
      val height: Int = 0,
      val lines: List<OverlayLine> = emptyList(),
  )

  private val NEUTRAL_COLOR = Color.rgb(red = 0xDD, green = 0xDD, blue = 0xDD)
  private val STALE_COLOR = Color.rgb(red = 0xAA, green = 0xAA, blue = 0xAA)
  private val HEALTHY_COLOR = Color.rgb(red = 0x55, green = 0xFF, blue = 0x55)
  private val DEGRADED_COLOR = Color.rgb(red = 0xFF, green = 0xFF, blue = 0x55)
  private val STRUGGLING_COLOR = Color.rgb(red = 0xFF, green = 0x55, blue = 0x55)
}
