package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso

import io.github.fopwoc.mods.palimpsest.client.motion.easeStep
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Where the map is seen from: [yaw] and [pitch] as in [IsoCamera], gliding to what the view cube or
 * a key asked for, and the [slice], the highest Y shown. North up, straight down and uncut is the
 * flat map; anything else is the 3D view.
 */
class MapOrbit {
    /** Unbounded, so a glide across north never takes the long way round. */
    var yaw = 0.0
        private set

    var pitch = IsoCamera.MAX_PITCH
        private set

    var slice: Int? = null
        private set

    private var targetYaw = yaw
    private var targetPitch = pitch

    /** The turn within 0 until 360. */
    val heading: Double
        get() = yaw - floor(yaw / FULL_TURN) * FULL_TURN

    val isFlat: Boolean
        get() = pitch == IsoCamera.MAX_PITCH && heading == 0.0 && slice == null

    /** Glides to look from compass [yaw] down at [pitch], the shorter way round. */
    fun lookFrom(yaw: Double, pitch: Double) {
        val turn = yaw - targetYaw
        targetYaw += turn - floor((turn + FULL_TURN / 2) / FULL_TURN) * FULL_TURN
        targetPitch = pitch.coerceIn(IsoCamera.MIN_PITCH, IsoCamera.MAX_PITCH)
    }

    /** Back to the flat map's angle; the slice stays as it is. */
    fun lookDown() = lookFrom(0.0, IsoCamera.MAX_PITCH)

    /** Turns by whole quarters from the nearest square heading, tilting off a top-down look. */
    fun turnQuarters(quarters: Int) {
        val square = (targetYaw / QUARTER).roundToLong() * QUARTER
        targetYaw = square + quarters * QUARTER
        if (targetPitch == IsoCamera.MAX_PITCH) targetPitch = ISOMETRIC_PITCH
    }

    /** Follows a drag one to one, cancelling any glide. */
    fun dragBy(yawDegrees: Double, pitchDegrees: Double) {
        yaw += yawDegrees
        pitch = (pitch + pitchDegrees).coerceIn(IsoCamera.MIN_PITCH, IsoCamera.MAX_PITCH)
        targetYaw = yaw
        targetPitch = pitch
    }

    /** After a drag: close to straight down lies flat, close to a square heading squares up. */
    fun settle() {
        if (pitch >= IsoCamera.MAX_PITCH - SNAP_PITCH) targetPitch = IsoCamera.MAX_PITCH
        val square = (yaw / QUARTER).roundToLong() * QUARTER
        if (abs(yaw - square) <= SNAP_YAW) targetYaw = square
    }

    /**
     * A drag of [dx], [dy] screen pixels in the 3D view as the drag of the flat map that moves the
     * same ground under the cursor, so panning, flings and zoom stay the flat map's own.
     */
    fun toFlat(dx: Double, dy: Double): Pair<Double, Double> {
        val turn = Math.toRadians(yaw)
        val depth = dy / sin(Math.toRadians(pitch))
        return dx * cos(turn) + depth * sin(turn) to -dx * sin(turn) + depth * cos(turn)
    }

    fun cutAt(y: Int?) {
        slice = y
    }

    /** Moves towards the target; true while anything moved. */
    fun advance(seconds: Double): Boolean {
        if (yaw == targetYaw && pitch == targetPitch) return false
        val step = easeStep(seconds, GLIDE_SECONDS)
        yaw = glide(yaw, targetYaw, step)
        pitch = glide(pitch, targetPitch, step)
        return true
    }

    private fun glide(from: Double, to: Double, step: Double): Double =
        if (abs(to - from) <= ARRIVED) to else from + (to - from) * step

    companion object {
        /** The angle a cube corner looks from: every axis equally foreshortened. */
        const val ISOMETRIC_PITCH = 35.264
        private const val FULL_TURN = 360.0
        private const val QUARTER = 90.0
        private const val GLIDE_SECONDS = 0.08
        private const val ARRIVED = 0.05
        private const val SNAP_PITCH = 4.0
        private const val SNAP_YAW = 4.0
    }
}
