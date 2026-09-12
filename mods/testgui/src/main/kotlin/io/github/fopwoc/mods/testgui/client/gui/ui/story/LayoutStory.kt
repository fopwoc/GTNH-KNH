package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

private val frame = Color(0xFF505050)
private val a = Color(0xA0C0504D)
private val b = Color(0xA04F81BD)
private val c = Color(0xA09BBB59)

@Composable
fun LayoutStory() {
  Examples {
    Example("Box: nine alignments") {
      Box(modifier = Modifier.fillMaxWidth().height(50.uu).border(frame)) {
        listOf(
                Alignment.TopStart,
                Alignment.TopCenter,
                Alignment.TopEnd,
                Alignment.CenterStart,
                Alignment.Center,
                Alignment.CenterEnd,
                Alignment.BottomStart,
                Alignment.BottomCenter,
                Alignment.BottomEnd,
            )
            .forEach { Box(modifier = Modifier.size(8.uu).background(a).align(it)) }
      }
    }
    Example("Row: weight 1 / 2 / fixed 30, spacedBy 4") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        Box(modifier = Modifier.weight(1f).height(12.uu).background(a))
        Box(modifier = Modifier.weight(2f).height(12.uu).background(b))
        Box(modifier = Modifier.width(30.uu).height(12.uu).background(c))
      }
    }
    Example("Row arrangements: SpaceBetween / Center / End") {
      listOf(
              HorizontalArrangement.SpaceBetween,
              HorizontalArrangement.Center,
              HorizontalArrangement.End,
          )
          .forEach { arrangement ->
            Row(
                modifier = Modifier.fillMaxWidth().border(frame),
                horizontalArrangement = arrangement,
            ) {
              repeat(3) { Box(modifier = Modifier.size(10.uu).background(b)) }
            }
          }
    }
    Example("Row vertical alignment TOP / CENTER / BOTTOM in a 24-high row") {
      Row(
          modifier = Modifier.fillMaxWidth().height(24.uu).border(frame),
          horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
      ) {
        Box(modifier = Modifier.size(8.uu).background(a).align(VerticalAlignment.TOP))
        Box(modifier = Modifier.size(8.uu).background(b).align(VerticalAlignment.CENTER))
        Box(modifier = Modifier.size(8.uu).background(c).align(VerticalAlignment.BOTTOM))
      }
    }
    Example("Column: fixed header + fillMaxSize child stays inside (remaining-space rule)") {
      Column(modifier = Modifier.fillMaxWidth().height(40.uu).border(frame)) {
        Text("header")
        Box(modifier = Modifier.fillMaxSize().background(c))
      }
    }
    Example("Spacer, padding, offset") {
      Row(modifier = Modifier.fillMaxWidth().border(frame)) {
        Box(modifier = Modifier.size(10.uu).background(a))
        Spacer(modifier = Modifier.width(20.uu))
        Box(modifier = Modifier.padding(4.uu).size(10.uu).background(b))
        Box(modifier = Modifier.offset(x = 10.uu, y = 4.uu).size(10.uu).background(c))
      }
    }
  }
}
