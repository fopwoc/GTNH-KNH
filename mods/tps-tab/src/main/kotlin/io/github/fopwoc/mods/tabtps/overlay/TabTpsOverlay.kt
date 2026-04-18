package io.github.fopwoc.mods.tabtps.overlay

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.monitor.TabTpsMonitor
import io.github.fopwoc.mods.tabtps.tps.TimedTpsMeasurement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraftforge.client.event.RenderGameOverlayEvent
import kotlin.math.max

@SideOnly(Side.CLIENT)
object TabTpsOverlay {
    private const val BOX_PADDING = 4
    private const val BOX_SPACING = 3

    @SubscribeEvent
    fun onRender(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST || !TabTpsConfig.enabled) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val fontRenderer = minecraft.fontRenderer ?: return
        val snapshot = TabTpsMonitor.snapshot()
        val tabBounds = computeTabBounds(minecraft, event.resolution.scaledWidth) ?: return
        val lines = buildLines(snapshot)
        if (lines.isEmpty()) {
            return
        }

        val boxWidth = lines.maxOf { fontRenderer.getStringWidth(it.text) } + BOX_PADDING * 2
        val boxHeight = lines.size * (fontRenderer.FONT_HEIGHT + 1) + BOX_PADDING * 2 - 1
        val boxLeft = max(2, tabBounds.left + tabBounds.width - boxWidth)
        val boxTop = tabBounds.top + tabBounds.height + BOX_SPACING

        Gui.drawRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, Color(0x78000000).argbInt)

        var y = boxTop + BOX_PADDING
        for (line in lines) {
            fontRenderer.drawStringWithShadow(line.text, boxLeft + BOX_PADDING, y, line.color.argbInt)
            y += fontRenderer.FONT_HEIGHT + 1
        }
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

    private fun computeTabBounds(minecraft: Minecraft, screenWidth: Int): TabBounds? {
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
        return TabBounds(
            left = left - 1,
            top = top,
            width = columns * columnWidth + 1,
            height = rows * 9 + 1
        )
    }

    private data class OverlayLine(
        val text: String,
        val color: Color
    )

    private data class TabBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )
}


