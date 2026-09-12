package io.github.fopwoc.mods.testgui.client.gui.ui.page.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.Scaffold
import io.github.fopwoc.mods.framework.ui.compose.component.Section
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/** Storybook: a list of stories on the left, the selected one on the right. */
@Composable
fun GalleryView(screenWidth: Int, screenHeight: Int, onClose: () -> Unit) {
  var selected by rememberSaveable { mutableIntStateOf(0) }
  val story = storyCatalog[selected.coerceIn(storyCatalog.indices)]

  Scaffold(
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      title = "KNH Core gallery",
      subtitle = story.title,
      onClose = onClose,
      maxWidth = 640,
      maxHeight = 400,
  ) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
    ) {
      Section(title = "Stories", modifier = Modifier.width(150.uu).fillMaxHeight()) {
        SelectableList(
            items = storyCatalog.map(Story::title),
            selectedIndex = selected,
            modifier = Modifier.fillMaxSize().weight(1f),
            rowHeight = 12.uu,
            onSelectedIndexChange = { selected = it },
        )
      }
      Section(
          title = story.title,
          modifier = Modifier.weight(1f).fillMaxHeight(),
          elevated = true,
      ) {
        // Every story scrolls if it is taller than the pane; `key` gives each its own scroll.
        key(story.title) {
          val scroll = rememberScrollState()
          Box(modifier = Modifier.fillMaxSize().weight(1f).verticalScroll(scroll)) {
            story.content()
          }
        }
      }
    }
  }
}
