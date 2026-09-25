package io.github.fopwoc.mods.palimpsest.client.map

/**
 * One map's place: [worldId] and [dimension] name directories (`maps/<world>/<dimension>/`), and
 * [ceiling] is the Y the surface scan looks down from, which names the slice (`y<ceiling>/`).
 */
data class MapLocation(val worldId: String, val dimension: String, val ceiling: Int) {
    val key: String
        get() = "$worldId/$dimension"
}
