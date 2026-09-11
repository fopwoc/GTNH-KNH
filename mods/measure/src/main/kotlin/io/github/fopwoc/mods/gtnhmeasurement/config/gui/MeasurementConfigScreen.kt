package io.github.fopwoc.mods.gtnhmeasurement.config.gui

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.gui.ConfigScreen
import io.github.fopwoc.mods.gtnhmeasurement.MOD_NAME
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class MeasurementConfigScreen(parent: GuiScreen) :
    ConfigScreen(parent, MeasurementConfig, "$MOD_NAME configuration")
