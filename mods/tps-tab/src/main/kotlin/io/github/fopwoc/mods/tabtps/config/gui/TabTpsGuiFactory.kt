package io.github.fopwoc.mods.tabtps.config.gui

import cpw.mods.fml.client.IModGuiFactory
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class TabTpsGuiFactory : IModGuiFactory {
  override fun initialize(minecraftInstance: Minecraft) = Unit

  override fun mainConfigGuiClass(): Class<out GuiScreen> = TabTpsConfigScreen::class.java

  override fun runtimeGuiCategories(): Set<IModGuiFactory.RuntimeOptionCategoryElement> = emptySet()

  override fun getHandlerFor(
      element: IModGuiFactory.RuntimeOptionCategoryElement
  ): IModGuiFactory.RuntimeOptionGuiHandler? = null
}
