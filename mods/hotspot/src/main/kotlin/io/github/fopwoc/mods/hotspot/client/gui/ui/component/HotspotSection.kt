package io.github.fopwoc.mods.hotspot.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.hotspot.client.gui.ui.theme.hotspotSectionTitleTextStyle

@Composable
fun HotspotSection(
    title: String,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  HotspotCard(modifier = modifier, elevated = elevated) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = VerticalArrangement.spacedBy(4.uu),
    ) {
      Text(text = title, modifier = Modifier.fillMaxWidth(), style = hotspotSectionTitleTextStyle())
      content()
    }
  }
}
