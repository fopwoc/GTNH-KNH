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

private const val BORDER = 1
private const val NORTH_GLYPH_WIDTH = 5
private const val NORTH_GLYPH_HEIGHT = 8

/**
 * The minimap in its corner, or the big map over most of the screen: the map canvas with the player
 * arrow over its centre, plus coordinates or a north mark.
 */
@Composable
internal fun MinimapView(model: MinimapModel, map: GpuCanvasState, marker: GpuCanvasState) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val layout = model.layout) {
            is MinimapLayout.Corner ->
                HudAnchor(
                    bounds = model.screenBounds(MINIMAP_SCREEN_MARGIN),
                    contentAlignment = layout.corner.alignment,
                ) {
                    Column(horizontalAlignment = layout.corner.alignment.horizontal) {
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
                            Arrow(marker)
                            model.north?.let { north ->
                                Text(
                                    "N",
                                    style = TextStyle(color = Color(0xFFFF5555)),
                                    modifier =
                                        Modifier.offset(
                                            x = (north.x - NORTH_GLYPH_WIDTH / 2).uu,
                                            y = (north.y - NORTH_GLYPH_HEIGHT / 2).uu,
                                        ),
                                )
                            }
                        }
                        model.coordinates?.let { Text(it) }
                    }
                }
            is MinimapLayout.Big ->
                HudAnchor(
                    bounds = model.screenBounds(BIG_MAP_SCREEN_MARGIN),
                    contentAlignment = Alignment.Center,
                ) {
                    GpuCanvas(
                        state = map,
                        modifier = Modifier.size(layout.width.uu, layout.height.uu),
                    )
                    Arrow(marker)
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
