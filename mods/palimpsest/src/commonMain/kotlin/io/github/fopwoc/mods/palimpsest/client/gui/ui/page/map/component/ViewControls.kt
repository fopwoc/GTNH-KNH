package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.MapViewAngle
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso.ViewCube
import kotlin.math.roundToInt

/**
 * The view cube drawn from [cube], with its face letters, quarter turns and the slice rail. The
 * cube and the rail take their drags from the screen; the turn buttons click as buttons do.
 */
@Composable
internal fun ViewControls(
    view: MapViewAngle,
    cube: GpuCanvasState,
    modifier: Modifier = Modifier,
    onTurn: (Int) -> Unit,
) {
    Box(modifier = modifier.size(ViewControlsLayout.WIDTH.uu, ViewControlsLayout.HEIGHT.uu)) {
        GpuCanvas(state = cube, modifier = Modifier.size(ViewCube.SIZE.uu))
        ViewCube(view.yaw, view.pitch).labels.forEach { label ->
            Text(
                label.text,
                modifier =
                    Modifier.offset(
                        (label.x - label.text.length * GLYPH_WIDTH / 2.0).roundToInt().uu,
                        (label.y - GLYPH_HEIGHT / 2.0).roundToInt().uu,
                    ),
                style = TextStyle(color = Color(0xFF20222C), shadow = false),
            )
        }
        Button(
            "<",
            modifier =
                Modifier.offset(0.uu, ViewControlsLayout.TURN_TOP.uu)
                    .size((ViewControlsLayout.WIDTH / 2 - 1).uu, ViewControlsLayout.TURN_HEIGHT.uu)
                    .tooltip("Turn the map a quarter left"),
        ) {
            onTurn(-1)
        }
        Button(
            ">",
            modifier =
                Modifier.offset(
                        (ViewControlsLayout.WIDTH / 2 + 1).uu,
                        ViewControlsLayout.TURN_TOP.uu,
                    )
                    .size((ViewControlsLayout.WIDTH / 2 - 1).uu, ViewControlsLayout.TURN_HEIGHT.uu)
                    .tooltip("Turn the map a quarter right"),
        ) {
            onTurn(1)
        }
        val handle = ViewControlsLayout.railY(view.slice, view.sliceRange)
        Box(
            modifier =
                Modifier.offset(ViewControlsLayout.RAIL_LEFT.uu, ViewControlsLayout.RAIL_TOP.uu)
                    .size(ViewControlsLayout.RAIL_WIDTH.uu, ViewControlsLayout.RAIL_HEIGHT.uu)
                    .background(Color(0xA0181A26))
                    .border(Color(0xFF454A5C))
                    .tooltip("Drag to cut the world away above a height; to the top shows it all")
        )
        Box(
            modifier =
                Modifier.offset(
                        ViewControlsLayout.RAIL_LEFT.uu,
                        (ViewControlsLayout.RAIL_TOP + handle).uu,
                    )
                    .size(
                        ViewControlsLayout.RAIL_WIDTH.uu,
                        (ViewControlsLayout.RAIL_HEIGHT - handle).uu,
                    )
                    .background(Color(0x607FB2FF))
        )
        Box(
            modifier =
                Modifier.offset(
                        (ViewControlsLayout.RAIL_LEFT - 2).uu,
                        (ViewControlsLayout.RAIL_TOP + handle - 2).uu,
                    )
                    .size((ViewControlsLayout.RAIL_WIDTH + 4).uu, 4.uu)
                    .background(Color(0xFFE6E6E6))
        )
        val label = view.slice?.let { "Y $it" } ?: "All"
        Text(
            label,
            modifier =
                Modifier.offset(
                    ((ViewControlsLayout.WIDTH - label.length * GLYPH_WIDTH) / 2).uu,
                    (ViewControlsLayout.RAIL_TOP + ViewControlsLayout.RAIL_HEIGHT + 3).uu,
                ),
        )
    }
}

/** The game font's usual glyph, for centring short labels. */
private const val GLYPH_WIDTH = 6
private const val GLYPH_HEIGHT = 8
