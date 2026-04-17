package io.github.fopwoc.mods.gtnhmeasurement.client.gui

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft

@SideOnly(Side.CLIENT)
object MeasurementScreenController {
    private var openRequested = false

    fun requestOpen() {
        openRequested = true
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END || !openRequested) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            openRequested = false
            return
        }

        openRequested = false
        minecraft.displayGuiScreen(MeasurementModeScreen())
    }
}

