/*
 * Hallmark · component: performance card · structure: instrument-panel
 * genre: modern-minimal · theme: Cobalt · contrast: pass · horizontal bounds: pass
 * interaction: none · content: live server metrics
 * pre-emit critique: P5 H5 E5 S5 R5 V4
 */
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
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeHudOverlay
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
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
import net.minecraft.client.gui.FontRenderer
import net.minecraftforge.client.event.RenderGameOverlayEvent

@SideOnly(Side.CLIENT)
object TabTpsOverlay {
  private const val SCREEN_MARGIN = 4
  private const val CARD_GAP = 4
  private const val CARD_PADDING = 4
  private const val ROW_SPACING = 4
  private const val DESIRED_CARD_WIDTH = 236
  private const val TPS_COLUMN_WIDTH = 42
  private const val MSPT_COLUMN_WIDTH = 51
  private const val COLUMN_SPACING = 4

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
    val geometry = cardGeometry(tabBounds, event.resolution.scaledWidth)
    val card = buildCard(snapshot, fontRenderer, geometry.contentWidth)
    if (card == null) {
      hideOverlay()
      return
    }

    overlayState =
        OverlayState(
            anchorBounds = geometry.anchorBounds,
            width = geometry.cardWidth,
            height = card.height(fontRenderer.FONT_HEIGHT),
            labelWidth = geometry.labelWidth,
            card = card,
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

  private fun buildCard(
      snapshot: TabTpsMonitor.Snapshot,
      fontRenderer: FontRenderer,
      contentWidth: Int,
  ): OverlayCard? {
    val measurement = snapshot.measurement
    if (measurement == null) {
      val status = snapshot.statusMessage
      return if (TabTpsConfig.showPlaceholder && status != null) {
        OverlayCard(
            status =
                OverlayText.ellipsize(
                    text = status,
                    maxWidth = contentWidth,
                    widthOf = fontRenderer::getStringWidth,
                    trimToWidth = fontRenderer::trimStringToWidth,
                ),
            stateLabel = "WAITING · ${TabTpsConfig.updateIntervalTicks}t",
        )
      } else {
        null
      }
    }

    val stale = snapshot.tickNow - measurement.receivedAtTick > TabTpsConfig.staleDataTicks
    val response = measurement.snapshot
    val labelWidth = contentWidth - TPS_COLUMN_WIDTH - MSPT_COLUMN_WIDTH - COLUMN_SPACING * 2
    val rows = buildList {
      add(metricRow("Server", response.server, stale, fontRenderer, labelWidth))
      response.dimensions.forEach { dimension ->
        val prefix = if (dimension.dimensionId == response.currentDimensionId) "Current · " else ""
        add(
            metricRow(
                "$prefix${dimension.dimensionName} #${dimension.dimensionId}",
                dimension.metrics,
                stale,
                fontRenderer,
                labelWidth,
            )
        )
      }
    }
    return OverlayCard(
        rows = rows,
        stateLabel = "${if (stale) "STALE" else "LIVE"} · ${TabTpsConfig.updateIntervalTicks}t",
    )
  }

  private fun metricRow(
      label: String,
      metrics: TpsMetrics,
      stale: Boolean,
      fontRenderer: FontRenderer,
      labelWidth: Int,
  ): MetricRow =
      MetricRow(
          label =
              OverlayText.ellipsize(
                  text = label,
                  maxWidth = labelWidth,
                  widthOf = fontRenderer::getStringWidth,
                  trimToWidth = fontRenderer::trimStringToWidth,
              ),
          tps = String.format(Locale.ROOT, "%.2f", metrics.tps),
          mspt = String.format(Locale.ROOT, "%.2f ms", metrics.mspt),
          metricColor = colorFor(metrics, stale),
      )

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

  private fun cardGeometry(tabBounds: HudRect, screenWidth: Int): CardGeometry {
    val safeRight =
        minOf(tabBounds.left + tabBounds.width, screenWidth - SCREEN_MARGIN)
            .coerceAtLeast(SCREEN_MARGIN + 1)
    val availableWidth = (safeRight - SCREEN_MARGIN).coerceAtLeast(1)
    val cardWidth = minOf(maxOf(tabBounds.width, DESIRED_CARD_WIDTH), availableWidth)
    val contentWidth = (cardWidth - CARD_PADDING * 2).coerceAtLeast(1)
    val labelWidth =
        (contentWidth - TPS_COLUMN_WIDTH - MSPT_COLUMN_WIDTH - COLUMN_SPACING * 2).coerceAtLeast(1)
    return CardGeometry(
        anchorBounds =
            HudRect(
                left = SCREEN_MARGIN,
                top = tabBounds.top,
                width = availableWidth,
                height = tabBounds.height,
            ),
        cardWidth = cardWidth,
        contentWidth = contentWidth,
        labelWidth = labelWidth,
    )
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
    val card = state.card ?: return

    Box(modifier = Modifier.fillMaxSize()) {
      HudAnchor(bounds = state.anchorBounds, contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier =
                Modifier.width(state.width.uu)
                    .height(state.height.uu)
                    .offset(y = CARD_GAP.uu)
                    .background(CARD_SURFACE)
                    .border(CARD_BORDER)
                    .padding(CARD_PADDING.uu),
            verticalArrangement = VerticalArrangement.spacedBy(ROW_SPACING.uu),
        ) {
          CardHeader(card.stateLabel)
          if (card.status != null) {
            Text(
                text = card.status,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = TEXT_PRIMARY),
            )
          } else {
            MetricHeader(state.labelWidth)
            card.rows.forEach { row -> MetricRowContent(row, state.labelWidth) }
          }
        }
      }
    }
  }

  @Composable
  private fun CardHeader(stateLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = HorizontalArrangement.SpaceBetween,
    ) {
      Text(text = "TPS TAB", style = TextStyle(color = TEXT_PRIMARY))
      Text(text = stateLabel, style = TextStyle(color = ACCENT_COLOR))
    }
  }

  @Composable
  private fun MetricHeader(labelWidth: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = HorizontalArrangement.spacedBy(COLUMN_SPACING.uu),
    ) {
      ColumnText("SCOPE", labelWidth, TEXT_MUTED)
      ColumnText("TPS", TPS_COLUMN_WIDTH, TEXT_MUTED, HorizontalAlignment.END)
      ColumnText("MSPT", MSPT_COLUMN_WIDTH, TEXT_MUTED, HorizontalAlignment.END)
    }
  }

  @Composable
  private fun MetricRowContent(row: MetricRow, labelWidth: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = HorizontalArrangement.spacedBy(COLUMN_SPACING.uu),
    ) {
      ColumnText(row.label, labelWidth, TEXT_PRIMARY)
      ColumnText(row.tps, TPS_COLUMN_WIDTH, row.metricColor, HorizontalAlignment.END)
      ColumnText(row.mspt, MSPT_COLUMN_WIDTH, TEXT_PRIMARY, HorizontalAlignment.END)
    }
  }

  @Composable
  private fun ColumnText(
      text: String,
      width: Int,
      color: Color,
      alignment: HorizontalAlignment = HorizontalAlignment.START,
  ) {
    Text(
        text = text,
        modifier = Modifier.width(width.uu),
        style = TextStyle(color = color, alignment = alignment),
    )
  }

  private data class CardGeometry(
      val anchorBounds: HudRect,
      val cardWidth: Int,
      val contentWidth: Int,
      val labelWidth: Int,
  )

  private data class OverlayCard(
      val rows: List<MetricRow> = emptyList(),
      val status: String? = null,
      val stateLabel: String,
  ) {
    fun height(fontHeight: Int): Int {
      val childCount = if (status != null) 2 else rows.size + 2
      return CARD_PADDING * 2 + childCount * fontHeight + (childCount - 1) * ROW_SPACING
    }
  }

  private data class MetricRow(
      val label: String,
      val tps: String,
      val mspt: String,
      val metricColor: Color,
  )

  private data class OverlayState(
      val anchorBounds: HudRect = HudRect.Zero,
      val width: Int = 0,
      val height: Int = 0,
      val labelWidth: Int = 0,
      val card: OverlayCard? = null,
  )

  private val CARD_SURFACE = Color(0xE80D1520)
  private val CARD_BORDER = Color(0xC0527394)
  private val TEXT_PRIMARY = Color.rgb(red = 0xF2, green = 0xF5, blue = 0xF8)
  private val TEXT_MUTED = Color.rgb(red = 0x9F, green = 0xB0, blue = 0xC0)
  private val ACCENT_COLOR = Color.rgb(red = 0x8F, green = 0xD0, blue = 0xFF)
  private val STALE_COLOR = Color.rgb(red = 0xAA, green = 0xAA, blue = 0xAA)
  private val HEALTHY_COLOR = Color.rgb(red = 0x55, green = 0xFF, blue = 0x55)
  private val DEGRADED_COLOR = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)
  private val STRUGGLING_COLOR = Color.rgb(red = 0xFF, green = 0x55, blue = 0x55)
}
