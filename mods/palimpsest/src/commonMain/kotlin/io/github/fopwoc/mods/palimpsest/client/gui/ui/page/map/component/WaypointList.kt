package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import java.util.UUID

/** A way back to waypoints that are outside the current map viewport. */
@Composable
internal fun WaypointList(
    waypoints: List<Waypoint>,
    modifier: Modifier,
    onSelect: (UUID) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = modifier.background(Color(0xE8181A25)).border(Color(0xFF59637A)).padding(6.uu),
        verticalArrangement = VerticalArrangement.spacedBy(5.uu),
    ) {
        Row(horizontalArrangement = HorizontalArrangement.spacedBy(4.uu)) {
            Text("Waypoints")
            Button("Close") { onClose() }
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = VerticalArrangement.spacedBy(3.uu),
        ) {
            if (waypoints.isEmpty()) Text("No waypoints yet")
            waypoints.forEach { waypoint ->
                Button(waypoint.name, modifier = Modifier.fillMaxWidth()) { onSelect(waypoint.id) }
            }
        }
    }
}
