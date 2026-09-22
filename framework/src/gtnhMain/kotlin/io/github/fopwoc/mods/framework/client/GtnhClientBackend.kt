package io.github.fopwoc.mods.framework.client

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayerHost
import io.github.fopwoc.mods.framework.ui.compose.minecraft.GtnhComposeScreenHost
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.GtnhRenderSurface
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftPrimitiveRenderCallbacks
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen
import kotlin.math.max
import kotlin.math.min
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.common.MinecraftForge

@SideOnly(Side.CLIENT)
class GtnhClientBackend : ClientBackend {
    private val hudLayers = mutableListOf<HudLayerHost>()

    override val isInWorld: Boolean
        get() = Minecraft.getMinecraft().let { it.thePlayer != null && it.theWorld != null }

    override val playerPosition: PlayerPosition?
        get() = Minecraft.getMinecraft().thePlayer?.let { PlayerPosition(it.posX, it.posY, it.posZ) }

    override fun openScreen(screen: ComposeScreen) = Minecraft.getMinecraft().displayGuiScreen(GtnhComposeScreenHost(screen))

    override fun registerHud(layer: HudLayer) {
        if (hudLayers.isEmpty()) MinecraftForge.EVENT_BUS.register(this)
        hudLayers += HudLayerHost(layer) { GtnhRenderSurface(HudPrimitives) }
    }

    override fun registerCommand(command: ClientCommand) {
        ClientCommandHandler.instance.registerCommand(GtnhClientCommand(command))
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return
        hudLayers.forEach { it.render(event.resolution.scaledWidth, event.resolution.scaledHeight) }
    }

    private object HudPrimitives : MinecraftPrimitiveRenderCallbacks {
        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) = Gui.drawRect(left, top, right, bottom, color)

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) =
            Gui.drawRect(min(startX, endX), y, max(startX, endX) + 1, y + 1, color)

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) =
            Gui.drawRect(x, min(startY, endY), x + 1, max(startY, endY) + 1, color)
    }
}
