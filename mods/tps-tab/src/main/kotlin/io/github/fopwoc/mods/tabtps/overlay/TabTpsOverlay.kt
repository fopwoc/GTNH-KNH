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
import io.github.fopwoc.mods.tabtps.tps.TimedTpsMeasurement
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent
import kotlin.math.max

@SideOnly(Side.CLIENT)
object TabTpsOverlay {
    private const val BOX_PADDING = 4
    private const val BOX_SPACING = 3

    private val overlayHost = ComposeHudOverlay {
        OverlayContent(overlayState)
    }

    private var overlayState by mutableStateOf(OverlayState())

    @SubscribeEvent
    fun onRender(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
            return
        }

        if (!TabTpsConfig.enabled) {
            hideOverlay()
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val fontRenderer = minecraft.fontRenderer ?: run {
            hideOverlay()
            return
        }
        val snapshot = TabTpsMonitor.snapshot()
        val tabBounds = computeTabBounds(minecraft, event.resolution.scaledWidth) ?: run {
            hideOverlay()
            return
        }
        val lines = buildLines(snapshot)
        if (lines.isEmpty()) {
            hideOverlay()
            return
        }

        val boxWidth = lines.maxOf { fontRenderer.getStringWidth(it.text) } + BOX_PADDING * 2
        val boxHeight = lines.size * (fontRenderer.FONT_HEIGHT + 1) + BOX_PADDING * 2 - 1

        overlayState = OverlayState(
            anchorBounds = tabBounds,
            width = boxWidth,
            height = boxHeight,
            lines = lines
        )
        overlayHost.render(
            client = minecraft,
            font = fontRenderer,
            width = event.resolution.scaledWidth,
            height = event.resolution.scaledHeight
        )
    }

    @SubscribeEvent
    fun onClientDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        hideOverlay()
    }

    private fun buildLines(snapshot: TabTpsMonitor.Snapshot): List<OverlayLine> {
        if (!snapshot.connected) {
            return emptyList()
        }

        val lines = mutableListOf<OverlayLine>()
        val overallText = formatMeasurement("Server", snapshot.overall, snapshot.tickNow)
        if (overallText != null) {
            lines.add(OverlayLine(overallText, colorFor(snapshot.overall, snapshot.tickNow)))
        } else if (TabTpsConfig.showPlaceholder) {
            lines.add(OverlayLine(snapshot.statusMessage ?: TabTpsConfig.placeholderText, Color.rgb(red = 0xDD, green = 0xDD, blue = 0xDD)))
        }

        val dimensionLabel = snapshot.currentDimensionName?.takeIf { it.isNotBlank() } ?: "Current dim"
        val dimensionText = formatMeasurement(dimensionLabel, snapshot.currentDimension, snapshot.tickNow)
        if (dimensionText != null) {
            lines.add(OverlayLine(dimensionText, colorFor(snapshot.currentDimension, snapshot.tickNow)))
        } else if (snapshot.statusMessage != null && lines.isEmpty()) {
            lines.add(OverlayLine(snapshot.statusMessage, Color.rgb(red = 0xDD, green = 0xDD, blue = 0xDD)))
        }

        return lines
    }

    private fun formatMeasurement(label: String, measurement: TimedTpsMeasurement?, tickNow: Long): String? {
        val metric = measurement?.measurement ?: return null
        val staleSuffix = if (tickNow - measurement.sampledAtTick > TabTpsConfig.staleDataTicks) " (stale)" else ""
        return "$label: ${"%.2f".format(metric.tps)} TPS · ${"%.2f".format(metric.mspt)} ms/t$staleSuffix"
    }

    private fun colorFor(measurement: TimedTpsMeasurement?, tickNow: Long): Color {
        val metric = measurement?.measurement ?: return Color.rgb(red = 0xDD, green = 0xDD, blue = 0xDD)
        if (tickNow - measurement.sampledAtTick > TabTpsConfig.staleDataTicks) {
            return Color.rgb(red = 0xAA, green = 0xAA, blue = 0xAA)
        }

        return when {
            metric.tps >= 19.5 -> Color.rgb(red = 0x55, green = 0xFF, blue = 0x55)
            metric.tps >= 18.0 -> Color.rgb(red = 0xFF, green = 0xFF, blue = 0x55)
            else -> Color.rgb(red = 0xFF, green = 0x55, blue = 0x55)
        }
    }

    private fun computeTabBounds(minecraft: Minecraft, screenWidth: Int): HudRect? {
        val player = minecraft.thePlayer ?: return null
        val world = minecraft.theWorld ?: return null
        val handler = player.sendQueue ?: return null
        val scoreObjective = world.scoreboard.func_96539_a(0)
        val playerCount = handler.playerInfoList.size

        if (!minecraft.gameSettings.keyBindPlayerList.getIsKeyPressed()
            || (minecraft.isIntegratedServerRunning() && playerCount <= 1 && scoreObjective == null)
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
        val top = 9
        return HudRect(
            left = left - 1,
            top = top,
            width = columns * columnWidth + 1,
            height = rows * 9 + 1
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
                    modifier = Modifier
                        .width(state.width.uu)
                        .height(state.height.uu)
                        .offset(y = BOX_SPACING.uu)
                        .background(Color(0x78000000))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(BOX_PADDING.uu),
                        verticalArrangement = VerticalArrangement.spacedBy(1.uu)
                    ) {
                        state.lines.forEach { line ->
                            Text(
                                text = line.text,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(color = line.color)
                            )
                        }
                    }
                }
            }
        }
    }

    private data class OverlayLine(
        val text: String,
        val color: Color
    )

    private data class OverlayState(
        val anchorBounds: HudRect = HudRect.Zero,
        val width: Int = 0,
        val height: Int = 0,
        val lines: List<OverlayLine> = emptyList()
    )
}
