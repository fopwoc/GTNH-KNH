package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.LazyColumn
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/** The strip's close-button header; the list below it is what scrolls. */
internal const val HISTORY_HEADER_HEIGHT = 26

/**
 * The snapshot strip down the right edge: a close button, then one row per entry with the selected
 * one highlighted in the middle. [MapHistoryState.advance] drives the scroll position.
 */
@Composable
internal fun MapHistoryPanel(history: MapHistoryState, height: Int, modifier: Modifier = Modifier) {
    val listHeight = (height - HISTORY_HEADER_HEIGHT).coerceAtLeast(1)
    val spacers = history.leadingSpacers(listHeight)
    val entries = history.entryCount
    Column(
        modifier =
            modifier
                .width(MapHistoryState.PANEL_WIDTH.uu)
                .height(height.uu)
                .background(Color(0xE0101118))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(HISTORY_HEADER_HEIGHT.uu).padding(3.uu),
            contentAlignment = Alignment.Center,
        ) {
            Button("Back to live", modifier = Modifier.fillMaxWidth()) { history.close() }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(listHeight.uu),
            state = history.list,
            itemHeight = MapHistoryState.ROW_HEIGHT.uu,
        ) {
            items(spacers) { Box(modifier = Modifier.height(MapHistoryState.ROW_HEIGHT.uu)) }
            items(entries) { index -> HistoryRow(history, index) }
            items(spacers) { Box(modifier = Modifier.height(MapHistoryState.ROW_HEIGHT.uu)) }
        }
    }
}

@Composable
private fun HistoryRow(history: MapHistoryState, index: Int) {
    val selected = index == history.entry
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(MapHistoryState.ROW_HEIGHT.uu)
                .padding(horizontal = 4.uu)
                .let { if (selected) it.background(Color(0xFF3A5A8C)) else it }
                .hoverBackground(Color(0x40FFFFFF))
                .clickable { history.select(index) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            history.label(index),
            style = TextStyle(color = if (selected) Color(0xFFFFFFFF) else Color(0xFFB8B8C4)),
        )
    }
}
