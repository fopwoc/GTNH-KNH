package io.github.fopwoc.mods.palimpsest.client.minimap

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.BoxScope
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.config.MinimapCorner

/** Side of the player arrow in GUI pixels. */
internal const val MINIMAP_MARKER_SIZE = PlayerMarker.SIZE / 2

/** Gap between the minimap and the screen edge. */
internal const val MINIMAP_SCREEN_MARGIN = 4

/** Gap between the big map and the screen edge. */
internal const val BIG_MAP_SCREEN_MARGIN = 24

/** The north badge: a white box one pixel around the font's "N". */
internal const val NORTH_BADGE_WIDTH = 7
internal const val NORTH_BADGE_HEIGHT = 9

private const val BORDER = 1

/**
 * The minimap in its corner, or the big map over most of the screen: the map canvas under the
 * entity dots, with the player arrow over its centre and, on a turning map, the north badge on its
 * edge; coordinates are centred under the minimap.
 */
@Composable
internal fun MinimapView(
    model: MinimapModel,
    map: GpuCanvasState,
    dots: GpuCanvasState,
    marker: GpuCanvasState,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val layout = model.layout) {
            is MinimapLayout.Corner ->
                HudAnchor(
                    bounds = model.screenBounds(MINIMAP_SCREEN_MARGIN),
                    contentAlignment = layout.corner.alignment,
                ) {
                    Column {
                        Box(
                            modifier =
                                Modifier.size((layout.size + 2 * BORDER).uu)
                                    .background(Color(0xC0101018))
                                    .padding(BORDER.uu)
                        ) {
                            GpuCanvas(
                                state = map,
                                modifier =
                                    Modifier.size(layout.size.uu).background(Color(0xFF0B0C12)),
                            )
                            GpuCanvas(state = dots, modifier = Modifier.size(layout.size.uu))
                            Arrow(marker)
                            model.north?.let { North(it) }
                        }
                        model.coordinates?.let {
                            Text(
                                it,
                                style = TextStyle(alignment = HorizontalAlignment.CENTER),
                                modifier = Modifier.width((layout.size + 2 * BORDER).uu),
                            )
                        }
                    }
                }
            is MinimapLayout.Big ->
                HudAnchor(
                    bounds = model.screenBounds(BIG_MAP_SCREEN_MARGIN),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.size(layout.width.uu, layout.height.uu)) {
                        GpuCanvas(state = map, modifier = Modifier.fillMaxSize())
                        GpuCanvas(state = dots, modifier = Modifier.fillMaxSize())
                        Arrow(marker)
                        model.north?.let { North(it) }
                    }
                }
        }
    }
}

@Composable
private fun BoxScope.Arrow(marker: GpuCanvasState) {
    GpuCanvas(
        state = marker,
        modifier = Modifier.align(Alignment.Center).size(MINIMAP_MARKER_SIZE.uu),
    )
}

/** A small white badge with a black "N", centred on [at]. */
@Composable
private fun North(at: MapMark) {
    Box(
        modifier =
            Modifier.offset(
                    x = (at.x - NORTH_BADGE_WIDTH / 2).uu,
                    y = (at.y - NORTH_BADGE_HEIGHT / 2).uu,
                )
                .size(NORTH_BADGE_WIDTH.uu, NORTH_BADGE_HEIGHT.uu)
                .background(Color(0xFFFFFFFF))
    ) {
        Text(
            "N",
            style = TextStyle(color = Color(0xFF000000), shadow = false),
            modifier = Modifier.offset(x = 1.uu, y = 1.uu),
        )
    }
}

private fun MinimapModel.screenBounds(margin: Int) =
    HudRect(
        left = margin,
        top = margin,
        width = screenWidth - 2 * margin,
        height = screenHeight - 2 * margin,
    )

private val MinimapCorner.alignment: Alignment
    get() =
        when (this) {
            MinimapCorner.TOP_LEFT -> Alignment.TopStart
            MinimapCorner.TOP_RIGHT -> Alignment.TopEnd
            MinimapCorner.BOTTOM_LEFT -> Alignment.BottomStart
            MinimapCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
