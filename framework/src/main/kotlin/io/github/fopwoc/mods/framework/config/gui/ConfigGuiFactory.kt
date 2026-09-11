package io.github.fopwoc.mods.framework.config.gui

import cpw.mods.fml.client.IModGuiFactory
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen

/**
 * Base for the `guiFactory` class a mod names in its `@Mod` annotation. Subclasses need a public
 * no-argument constructor and return their [ConfigScreen] from [screenClass].
 */
@SideOnly(Side.CLIENT)
abstract class ConfigGuiFactory : IModGuiFactory {
  protected abstract fun screenClass(): Class<out GuiScreen>

  override fun initialize(minecraftInstance: Minecraft) = Unit

  override fun mainConfigGuiClass(): Class<out GuiScreen> = screenClass()

  override fun runtimeGuiCategories(): Set<IModGuiFactory.RuntimeOptionCategoryElement> = emptySet()

  override fun getHandlerFor(
      element: IModGuiFactory.RuntimeOptionCategoryElement
  ): IModGuiFactory.RuntimeOptionGuiHandler? = null
}
