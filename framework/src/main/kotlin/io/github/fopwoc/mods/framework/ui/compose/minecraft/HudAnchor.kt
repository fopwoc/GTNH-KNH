package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.BoxScope
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
@SideOnly(Side.CLIENT)
fun BoxScope.HudAnchor(
    bounds: HudRect,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
  Box(
      modifier =
          modifier
              .width(bounds.width.coerceAtLeast(0).uu)
              .height(bounds.height.coerceAtLeast(0).uu)
              .offset(x = bounds.left.uu, y = bounds.top.uu)
              .align(Alignment.TopStart),
      contentAlignment = contentAlignment,
      content = content,
  )
}
