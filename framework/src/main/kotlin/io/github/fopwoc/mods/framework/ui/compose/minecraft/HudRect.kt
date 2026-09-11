package io.github.fopwoc.mods.framework.ui.compose.minecraft

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
data class HudRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
  companion object {
    val Zero: HudRect =
        HudRect(
            left = 0,
            top = 0,
            width = 0,
            height = 0,
        )
  }
}
