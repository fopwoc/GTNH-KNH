package io.github.fopwoc.mods.palimpsest.client.minimap

import kotlin.math.cos
import kotlin.math.sin

/** A clockwise turn of the map on screen, where y points down. */
internal class MapTurn(val degrees: Float) {
    private val cos = cos(Math.toRadians(degrees.toDouble()))
    private val sin = sin(Math.toRadians(degrees.toDouble()))

    /** Screen x of the offset ([dx], [dy]) from the map's centre after the turn. */
    fun x(dx: Double, dy: Double): Double = dx * cos - dy * sin

    /** Screen y of the offset ([dx], [dy]) from the map's centre after the turn. */
    fun y(dx: Double, dy: Double): Double = dx * sin + dy * cos

    companion object {
        val NONE = MapTurn(0f)

        /** The turn that puts a player facing [yaw] (Minecraft degrees) at the top. */
        fun headingUp(yaw: Float) = MapTurn(HALF_TURN - yaw)

        private const val HALF_TURN = 180f
    }
}
