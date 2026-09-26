package io.github.fopwoc.mods.palimpsest.client.motion

import kotlin.math.abs
import kotlin.math.exp

/** The seconds between the frames an animation advances on. */
class FrameClock(private val maxSeconds: Double = Double.POSITIVE_INFINITY) {
    private var lastNanos = 0L

    /** Seconds since the previous call, at most [maxSeconds]; zero on the first. */
    fun tick(nowNanos: Long): Double {
        val seconds =
            if (lastNanos == 0L) 0.0
            else ((nowNanos - lastNanos) / NANOS_PER_SECOND).coerceIn(0.0, maxSeconds)
        lastNanos = nowNanos
        return seconds
    }

    companion object {
        const val NANOS_PER_SECOND = 1e9
    }
}

/**
 * The share of the remaining way an exponential glide with time constant [seconds] covers in [dt]
 * seconds; frame rate independent.
 */
fun easeStep(dt: Double, seconds: Double): Double = 1 - exp(-dt / seconds)

/**
 * A point gliding after something that moves once a tick, so it moves every frame instead; a jump
 * of more than [SNAP_BLOCKS] on any axis, a teleport, lands at once.
 */
class GlidingPoint(x: Double, y: Double, z: Double) {
    var x = x
        private set

    var y = y
        private set

    var z = z
        private set

    fun follow(targetX: Double, targetY: Double, targetZ: Double, dt: Double) {
        if (
            abs(targetX - x) > SNAP_BLOCKS ||
                abs(targetY - y) > SNAP_BLOCKS ||
                abs(targetZ - z) > SNAP_BLOCKS
        ) {
            x = targetX
            y = targetY
            z = targetZ
            return
        }
        val step = easeStep(dt, EASE_SECONDS)
        x += (targetX - x) * step
        y += (targetY - y) * step
        z += (targetZ - z) * step
    }

    private companion object {
        /** About one tick: the point never trails its target by more than a frame or two. */
        const val EASE_SECONDS = 0.05
        /** Farther than anything walks in a tick. */
        const val SNAP_BLOCKS = 32.0
    }
}
