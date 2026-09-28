package io.github.fopwoc.mods.palimpsest.client.prospecting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudPlacement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.client.waypoint.ScreenPosition
import io.github.fopwoc.mods.palimpsest.client.waypoint.WaypointProjection

/** Nearby discovered deposits projected onto first-person play. */
object ProspectingHudLayer : HudLayer("palimpsest:prospecting", HudPlacement.BELOW_DEBUG) {
    private var marks by mutableStateOf<List<ShownMark>>(emptyList())

    override val visible: Boolean
        get() {
            val client = ClientBackend.current
            return client.isInWorld &&
                !client.isHudHidden &&
                !client.isScreenOpen &&
                MapSessions.session?.prospectingAvailable == true
        }

    override fun beforeFrame() {
        val session = MapSessions.session
        val camera = MapSessions.waypointCamera()
        val enabled = ProspectingLayers.enabled.value
        marks =
            if (session == null || camera == null) emptyList()
            else
                session.prospectingMarks.value
                    .asSequence()
                    .filter(enabled::shows)
                    .filter { mark ->
                        val dx = mark.x - camera.x
                        val dz = mark.z - camera.z
                        dx * dx + dz * dz <= MAX_DISTANCE * MAX_DISTANCE
                    }
                    .mapNotNull { mark ->
                        WaypointProjection.screenPosition(
                                mark.x.toDouble(),
                                mark.y?.toDouble() ?: camera.y,
                                mark.z.toDouble(),
                                camera,
                                width,
                                height,
                            )
                            ?.let { ShownMark(mark, it) }
                    }
                    .sortedBy { it.at.distance }
                    .take(MAX_MARKERS)
                    .toList()
    }

    @Composable
    override fun Content() {
        Box(modifier = Modifier.fillMaxSize()) {
            marks.forEach { (mark, at) ->
                Column(
                    modifier =
                        Modifier.offset((at.x - LABEL_WIDTH / 2).uu, (at.y - 8).uu)
                            .width(LABEL_WIDTH.uu)
                ) {
                    Text(
                        "${mark.kind.symbol()} · ${mark.name.take(MAX_NAME_LENGTH)} · ${at.distance}m",
                        modifier = Modifier.background(Color(0xD0181A26)).border(mark.kind.color()),
                    )
                }
            }
        }
    }

    private data class ShownMark(val mark: ProspectingMark, val at: ScreenPosition)

    private const val MAX_DISTANCE = 256.0
    private const val MAX_MARKERS = 6
    private const val LABEL_WIDTH = 124
    private const val MAX_NAME_LENGTH = 16
}
