package io.github.fopwoc.mods.palimpsest.client.waypoint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.IconItem
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudPlacement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions

/** Tracked waypoints projected over first-person play, including edge markers off screen. */
object WaypointHudLayer : HudLayer("palimpsest:waypoints", HudPlacement.BELOW_DEBUG) {
    private var marks by mutableStateOf<List<WaypointHudMark>>(emptyList())

    override val visible: Boolean
        get() {
            val client = ClientBackend.current
            return client.isInWorld &&
                !client.isHudHidden &&
                !client.isScreenOpen &&
                MapSessions.session != null
        }

    override fun beforeFrame() {
        val session = MapSessions.session
        val camera = MapSessions.waypointCamera()
        marks =
            if (session == null || camera == null) emptyList()
            else
                session.waypoints.entries.value
                    .asSequence()
                    .filter { it.tracked }
                    .mapNotNull { WaypointProjection.project(it, camera, width, height) }
                    .sortedBy(WaypointHudMark::distance)
                    .take(MAX_MARKERS)
                    .toList()
    }

    @Composable
    override fun Content() {
        Box(modifier = Modifier.fillMaxSize()) {
            marks.forEach { mark ->
                Column(
                    modifier =
                        Modifier.offset((mark.x - LABEL_WIDTH / 2).uu, (mark.y - ICON_SIZE / 2).uu)
                            .width(LABEL_WIDTH.uu),
                    horizontalAlignment = HorizontalAlignment.CENTER,
                ) {
                    IconItem(
                        mark.waypoint.icon,
                        Modifier.size(ICON_SIZE.uu)
                            .background(Color(0xD0181A26))
                            .border(Color(0xFFF2CF69)),
                    )
                    Text(
                        if (mark.atEdge) "${mark.distance}m"
                        else "${mark.waypoint.name.take(MAX_NAME_LENGTH)} · ${mark.distance}m"
                    )
                }
            }
        }
    }

    private const val ICON_SIZE = 18
    private const val LABEL_WIDTH = 112
    private const val MAX_NAME_LENGTH = 15
    private const val MAX_MARKERS = 8
}
