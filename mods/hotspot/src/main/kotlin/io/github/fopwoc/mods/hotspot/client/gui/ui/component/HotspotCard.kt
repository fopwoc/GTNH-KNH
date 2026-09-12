package io.github.fopwoc.mods.hotspot.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.hotspot.client.gui.ui.theme.HotspotPalette

@Composable
fun HotspotCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  Panel(
      modifier = modifier,
      backgroundColor =
          if (elevated) HotspotPalette.ElevatedBackground else HotspotPalette.SurfaceBackground,
      borderColor = HotspotPalette.SurfaceBorder,
      content = content,
  )
}
