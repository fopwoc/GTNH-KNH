package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.palimpsest.client.waypoint.WaypointCamera

/** What the map needs from the game it runs in; one implementation per Minecraft version. */
interface MapPlatform {
    /** Where the client is, or null outside a world. */
    fun location(): MapLocation?

    /** Biome tint lookups by the biome ids this platform writes into tiles. */
    fun biomeTints(): BiomeTints

    /** Walks loaded chunks and hands their tiles to [session]'s map. */
    fun scanner(session: MapSession): MapScanner

    /** How the map classifies the blocks below the player, for `/palimpsest block`. */
    fun describeBlocksBelow(): String

    /** The player's held item, for choosing a waypoint's inventory icon. */
    fun heldItemId(): ItemId?

    /** First-person view for projecting tracked waypoints into the HUD. */
    fun waypointCamera(): WaypointCamera?
}
