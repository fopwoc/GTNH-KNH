package io.github.fopwoc.mods.tabtps.monitor

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.tps.DimensionDescriptor
import io.github.fopwoc.mods.tabtps.tps.OpisTpsTextParser
import io.github.fopwoc.mods.tabtps.tps.ParsedOpisReport
import io.github.fopwoc.mods.tabtps.tps.TimeSyncTpsEstimator
import io.github.fopwoc.mods.tabtps.tps.TimedTpsMeasurement
import io.github.fopwoc.mods.tabtps.tps.TpsSource
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import java.util.LinkedHashSet
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiPlayerInfo
import net.minecraft.network.play.server.S03PacketTimeUpdate
import net.minecraft.scoreboard.Score
import net.minecraft.scoreboard.ScoreObjective
import net.minecraft.scoreboard.ScorePlayerTeam
import net.minecraft.util.EnumChatFormatting
import net.minecraft.world.World

@SideOnly(Side.CLIENT)
object TabTpsMonitor {
  private const val TIME_SYNC_HANDLER_NAME = "tab_tps_time_sync"

  data class Snapshot(
      val tickNow: Long,
      val connected: Boolean,
      val overall: TimedTpsMeasurement?,
      val currentDimension: TimedTpsMeasurement?,
      val currentDimensionName: String?,
      val currentDimensionId: Int?,
      val statusMessage: String?,
  )

  @Volatile private var tickCounter = 0L

  @Volatile private var connected = false
  @Volatile private var currentDimensionId: Int? = null

  private var currentDescriptor: DimensionDescriptor? = null
  private var overallMeasurement: TimedTpsMeasurement? = null
  private var dimensionMeasurement: TimedTpsMeasurement? = null
  private var statusMessage: String? = null
  private val timeSyncEstimator = TimeSyncTpsEstimator()

  fun snapshot(): Snapshot {
    return Snapshot(
        tickNow = tickCounter,
        connected = connected,
        overall = overallMeasurement,
        currentDimension = dimensionMeasurement,
        currentDimensionName = currentDescriptor?.displayName,
        currentDimensionId = currentDescriptor?.id,
        statusMessage = statusMessage,
    )
  }

  @SubscribeEvent
  fun onClientConnected(event: FMLNetworkEvent.ClientConnectedToServerEvent) {
    resetState()
    connected = true
  }

  @SubscribeEvent
  fun onClientDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
    resetState()
  }

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END) {
      return
    }

    tickCounter++
    TabTpsConfig.refreshIfChanged()

    val minecraft = Minecraft.getMinecraft()
    val world = minecraft.theWorld
    val player = minecraft.thePlayer
    if (world == null || player == null) {
      connected = false
      currentDescriptor = null
      currentDimensionId = null
      statusMessage = null
      return
    }

    connected = true
    updateDimensionDescriptor(world)
    ensureTimeSyncHook(minecraft)
    refreshMeasurements(minecraft)
  }

  private fun refreshMeasurements(minecraft: Minecraft) {
    val descriptor = currentDescriptor
    val passiveReport = OpisTpsTextParser.parse(collectOpisLines(minecraft), descriptor)
    applyOpisReport(passiveReport)

    if (passiveReport.overall == null) {
      overallMeasurement = timeSyncEstimator.latest() ?: overallMeasurement
    }

    statusMessage =
        when {
          passiveReport.overall != null || passiveReport.currentDimension != null -> null
          overallMeasurement?.source == TpsSource.TIME_SYNC_ESTIMATE ->
              "Server TPS is estimated from world time sync packets"
          else -> "No passive TPS source detected yet"
        }
  }

  private fun applyOpisReport(report: ParsedOpisReport) {
    if (report.overall != null) {
      overallMeasurement =
          TimedTpsMeasurement(
              measurement = report.overall,
              sampledAtTick = tickCounter,
              source = TpsSource.PASSIVE_TEXT,
              rawLine = report.overallLine,
          )
    }

    if (report.currentDimension != null) {
      dimensionMeasurement =
          TimedTpsMeasurement(
              measurement = report.currentDimension,
              sampledAtTick = tickCounter,
              source = TpsSource.PASSIVE_TEXT,
              rawLine = report.dimensionLine,
          )
    }
  }

  private fun ensureTimeSyncHook(minecraft: Minecraft) {
    val channel = minecraft.thePlayer?.sendQueue?.networkManager?.channel() ?: return
    if (channel.pipeline().context(TIME_SYNC_HANDLER_NAME) != null) {
      return
    }

    val installHandler = Runnable {
      val pipeline = channel.pipeline()
      if (pipeline.context(TIME_SYNC_HANDLER_NAME) != null) {
        return@Runnable
      }

      if (pipeline.context("packet_handler") != null) {
        pipeline.addBefore("packet_handler", TIME_SYNC_HANDLER_NAME, TimeSyncChannelHandler())
      } else {
        pipeline.addLast(TIME_SYNC_HANDLER_NAME, TimeSyncChannelHandler())
      }
    }

    if (channel.eventLoop().inEventLoop()) {
      installHandler.run()
    } else {
      channel.eventLoop().execute(installHandler)
    }
  }

  private fun collectOpisLines(minecraft: Minecraft): List<String> {
    val lines = LinkedHashSet<String>()
    val world = minecraft.theWorld ?: return emptyList()
    val player = minecraft.thePlayer ?: return emptyList()
    val handler = player.sendQueue ?: return emptyList()

    for (entry in handler.playerInfoList) {
      val info = entry as? GuiPlayerInfo ?: continue
      val name = stripFormatting(info.name)
      if (OpisTpsTextParser.looksLikeOpisLine(name)) {
        lines.add(name)
      }
    }

    val scoreboard = world.scoreboard
    for (slot in 0..2) {
      val objective = scoreboard.func_96539_a(slot) ?: continue
      addScoreboardObjectiveLines(lines, scoreboard, objective)
    }

    return lines.toList()
  }

  @Suppress("UNCHECKED_CAST")
  private fun addScoreboardObjectiveLines(
      lines: LinkedHashSet<String>,
      scoreboard: net.minecraft.scoreboard.Scoreboard,
      objective: ScoreObjective,
  ) {
    val displayName = stripFormatting(objective.displayName)
    if (OpisTpsTextParser.looksLikeOpisLine(displayName)) {
      lines.add(displayName)
    }

    val scores = scoreboard.func_96534_i(objective) as Collection<Score>
    for (score in scores.take(15)) {
      if (score.playerName.startsWith("#")) {
        continue
      }

      val team = scoreboard.getPlayersTeam(score.playerName)
      val playerName = stripFormatting(ScorePlayerTeam.formatPlayerName(team, score.playerName))
      val combined = stripFormatting("$playerName ${score.scorePoints}")
      if (OpisTpsTextParser.looksLikeOpisLine(playerName)) {
        lines.add(playerName)
      }
      if (OpisTpsTextParser.looksLikeOpisLine(combined)) {
        lines.add(combined)
      }
    }
  }

  private fun updateDimensionDescriptor(world: World) {
    val descriptor = buildDimensionDescriptor(world)
    if (descriptor == currentDescriptor) {
      return
    }

    currentDescriptor = descriptor
    currentDimensionId = descriptor.id
    dimensionMeasurement = null
  }

  private fun buildDimensionDescriptor(world: World): DimensionDescriptor {
    val dimensionId = world.provider.dimensionId
    val rawName = world.provider.dimensionName ?: "Dimension $dimensionId"
    val aliases = linkedSetOf<String>()
    fun addAlias(value: String) {
      val normalized = value.trim().lowercase()
      if (normalized.isNotBlank()) {
        aliases.add(normalized)
      }
    }

    addAlias(rawName)
    addAlias(rawName.replace("_", " "))
    addAlias(rawName.replace("_", ""))
    addAlias("dimension $dimensionId")
    addAlias("dim $dimensionId")

    when (dimensionId) {
      0 -> {
        addAlias("overworld")
        addAlias("surface")
        addAlias("world")
      }

      -1 -> {
        addAlias("nether")
        addAlias("the nether")
        addAlias("hell")
      }

      1 -> {
        addAlias("end")
        addAlias("the end")
        addAlias("ender")
      }
    }

    return DimensionDescriptor(
        id = dimensionId,
        displayName = rawName,
        aliases = aliases,
    )
  }

  private fun stripFormatting(text: String): String {
    return EnumChatFormatting.getTextWithoutFormattingCodes(text)?.trim().orEmpty()
  }

  private fun resetState() {
    tickCounter = 0L
    connected = false
    currentDimensionId = null
    currentDescriptor = null
    overallMeasurement = null
    dimensionMeasurement = null
    statusMessage = null
    timeSyncEstimator.reset()
  }

  private class TimeSyncChannelHandler : ChannelDuplexHandler() {
    override fun channelRead(context: ChannelHandlerContext, message: Any) {
      if (message is S03PacketTimeUpdate) {
        currentDimensionId?.let { dimensionId ->
          timeSyncEstimator.recordServerTime(
              dimensionId = dimensionId,
              totalWorldTime = message.func_149366_c(),
              sampledAtTick = tickCounter,
          )
        }
      }

      super.channelRead(context, message)
    }
  }
}
