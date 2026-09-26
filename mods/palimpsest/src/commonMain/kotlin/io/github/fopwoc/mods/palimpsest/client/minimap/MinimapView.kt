package io.github.fopwoc.mods.palimpsest.client.minimap

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.config.MinimapCorner

/** Side of the player arrow in GUI pixels. */
internal const val MINIMAP_MARKER_SIZE = PlayerMarker.SIZE / 2

private const val SCREEN_MARGIN = 4
private const val BORDER = 1

/** The minimap in its corner: the map canvas, the player arrow over its centre, coordinates. */
@Composable
internal fun MinimapView(model: MinimapModel, map: GpuCanvasState, marker: GpuCanvasState) {
    Box(modifier = Modifier.fillMaxSize()) {
        HudAnchor(
            bounds =
                HudRect(
                    left = SCREEN_MARGIN,
                    top = SCREEN_MARGIN,
                    width = model.screenWidth - 2 * SCREEN_MARGIN,
                    height = model.screenHeight - 2 * SCREEN_MARGIN,
                ),
            contentAlignment = model.corner.alignment,
        ) {
            Column(horizontalAlignment = model.corner.alignment.horizontal) {
                Box(
                    modifier =
                        Modifier.size((model.size + 2 * BORDER).uu)
                            .background(Color(0xC0101018))
                            .padding(BORDER.uu)
                ) {
                    GpuCanvas(
                        state = map,
                        modifier = Modifier.size(model.size.uu).background(Color(0xFF0B0C12)),
                    )
                    GpuCanvas(
                        state = marker,
                        modifier = Modifier.align(Alignment.Center).size(MINIMAP_MARKER_SIZE.uu),
                    )
                }
                model.coordinates?.let { Text(it) }
            }
        }
    }
}

private val MinimapCorner.alignment: Alignment
    get() =
        when (this) {
            MinimapCorner.TOP_LEFT -> Alignment.TopStart
            MinimapCorner.TOP_RIGHT -> Alignment.TopEnd
            MinimapCorner.BOTTOM_LEFT -> Alignment.BottomStart
            MinimapCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
