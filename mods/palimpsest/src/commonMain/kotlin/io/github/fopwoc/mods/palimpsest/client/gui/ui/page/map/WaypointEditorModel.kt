package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import java.util.UUID

/** The place and initial values being edited in the map screen. */
internal data class WaypointEditorModel(
    val id: UUID,
    val isNew: Boolean,
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val icon: ItemId,
    val tracked: Boolean,
) {
    companion object {
        fun from(waypoint: Waypoint) =
            WaypointEditorModel(
                waypoint.id,
                false,
                waypoint.name,
                waypoint.x,
                waypoint.y,
                waypoint.z,
                waypoint.icon,
                waypoint.tracked,
            )

        fun new(x: Int, y: Int, z: Int) =
            WaypointEditorModel(
                UUID.randomUUID(),
                true,
                "Waypoint",
                x,
                y,
                z,
                ItemId("minecraft:compass"),
                true,
            )
    }
}
