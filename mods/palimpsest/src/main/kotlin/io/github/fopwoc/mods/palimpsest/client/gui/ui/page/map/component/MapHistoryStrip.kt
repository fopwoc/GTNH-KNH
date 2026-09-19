package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.LazyColumn
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapHistoryModel
import kotlin.math.roundToInt

/**
 * The snapshot strip: a close button, then one row per entry with the selected one highlighted and
 * kept in the middle by following [MapHistoryModel.position]. Only the model moves the strip; a
 * scroll that did not come from it (the thumb was dragged) is reported through [onScrolled] as the
 * row position now in the middle.
 */
@Composable
internal fun MapHistoryStrip(
    model: MapHistoryModel,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
    onScrolled: (Double) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val listHeight = (height - HEADER_HEIGHT).coerceAtLeast(1)
    // Empty rows above and below the entries so the first and last can still sit centred.
    val spacers = (listHeight / 2) / ROW_HEIGHT
    val centre = (listHeight - ROW_HEIGHT) / 2.0
    val list = remember { LazyListState() }
    val sync = remember { ScrollSync() }
    SideEffect {
        val scroll = list.scroll.value
        if (sync.observed >= 0 && scroll != sync.observed) {
            onScrolled((scroll + centre) / ROW_HEIGHT - spacers)
        }
        list.scroll.scrollTo(
            ((model.position + spacers) * ROW_HEIGHT - centre).roundToInt().coerceAtLeast(0)
        )
        // Layout clamps the request, so remember what the state settled on, not what was asked.
        sync.observed = list.scroll.value
    }
    Column(modifier = modifier.width(width.uu).height(height.uu).background(Color(0xE0101118))) {
        Box(
            modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT.uu).padding(3.uu),
            contentAlignment = Alignment.Center,
        ) {
            Button("Back to live", modifier = Modifier.fillMaxWidth()) { onClose() }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(listHeight.uu),
            state = list,
            itemHeight = ROW_HEIGHT.uu,
        ) {
            items(spacers) { Box(modifier = Modifier.height(ROW_HEIGHT.uu)) }
            items(model.labels.size) { index ->
                HistoryRow(model.labels[index], selected = index == model.entry) {
                    onSelect(index)
                }
            }
            items(spacers) { Box(modifier = Modifier.height(ROW_HEIGHT.uu)) }
        }
    }
}

@Composable
private fun HistoryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(ROW_HEIGHT.uu)
                .padding(horizontal = 4.uu)
                .let { if (selected) it.background(Color(0xFF3A5A8C)) else it }
                .hoverBackground(Color(0x40FFFFFF))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            style = TextStyle(color = if (selected) Color(0xFFFFFFFF) else Color(0xFFB8B8C4)),
        )
    }
}

private class ScrollSync {
    var observed = -1
}

private const val HEADER_HEIGHT = 26
private const val ROW_HEIGHT = 14
