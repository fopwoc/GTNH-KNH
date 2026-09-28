package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.IconItem
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import kotlin.math.roundToInt

/** Inventory icons anchored to visible world positions on the full map. */
@Composable
internal fun MapWaypointLayer(camera: MapCamera, waypoints: List<Waypoint>) {
    waypoints.forEach { waypoint ->
        val (screenX, screenY) = camera.screenAt(waypoint.x + 0.5, waypoint.z + 0.5)
        if (
            screenX < -ICON_SIZE ||
                screenY < -ICON_SIZE ||
                screenX > camera.width + ICON_SIZE ||
                screenY > camera.height + ICON_SIZE
        )
            return@forEach
        IconItem(
            item = waypoint.icon,
            modifier =
                Modifier.offset(
                        (screenX - ICON_SIZE / 2).roundToInt().uu,
                        (screenY - ICON_SIZE / 2).roundToInt().uu,
                    )
                    .size(ICON_SIZE.uu)
                    .background(Color(0xD0181A26))
                    .border(Color(0xFFF2CF69))
                    .tooltip("${waypoint.name} · ${waypoint.x}, ${waypoint.y}, ${waypoint.z}"),
        )
    }
}

private const val ICON_SIZE = 18
