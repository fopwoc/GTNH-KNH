package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneLight
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The light over the maps at a time of day: the sun crossing from east to west, warm and low at
 * dawn and dusk and white at noon; by night the moon, cooler and dimmer, though never so dark the
 * map stops reading. The path leans a little south, so north and south walls differ.
 */
object SkyLight {
    /** The light the maps are drawn in now: the game's sky, unless the settings fix it. */
    fun current(sunAngle: () -> Double?): GpuSceneLight =
        if (PalimpsestConfig.mapDaylight) at(sunAngle()) else GpuSceneLight.FIXED

    /** Light for the sun at [angle] as [celestialAngle] gives it; the fixed light for none. */
    fun at(angle: Double?): GpuSceneLight {
        if (angle == null) return GpuSceneLight.FIXED
        val turn = angle * 2 * PI
        val sunX = -sin(turn)
        val sunY = cos(turn) * cos(TILT)
        val sunZ = cos(turn) * sin(TILT)
        val day = rise(sunY)
        val night = rise(-sunY)
        return if (day >= night)
            GpuSceneLight(
                sunX,
                max(sunY, MIN_ELEVATION),
                sunZ,
                color = blend(DAWN, NOON, smooth(0.0, HIGH_SUN, sunY)),
                intensity = day.toFloat(),
                ambient = (NIGHT_AMBIENT + (DAY_AMBIENT - NIGHT_AMBIENT) * day).toFloat(),
            )
        else
            GpuSceneLight(
                -sunX,
                max(-sunY, MIN_ELEVATION),
                -sunZ,
                color = MOON,
                intensity = (MOON_STRENGTH * night).toFloat(),
                ambient = NIGHT_AMBIENT.toFloat(),
            )
    }

    /**
     * The game's sun angle for [dayTime] ticks into the day cycle, 0 at noon: a day of 24000 ticks,
     * eased so days and nights pass quicker near the horizon, as vanilla turns its sky.
     */
    fun celestialAngle(dayTime: Long): Double {
        val day = Math.floorMod(dayTime, DAY_TICKS) / DAY_TICKS.toDouble() - QUARTER
        val wrapped = if (day < 0) day + 1 else day
        val eased = 1 - (cos(wrapped * PI) + 1) / 2
        return wrapped + (eased - wrapped) / EASE_SHARE
    }

    /** How far up a light at elevation [y] has come: nothing below the horizon, all well above. */
    private fun rise(y: Double) = smooth(-HORIZON, RISEN, y)

    private fun smooth(from: Double, to: Double, value: Double): Double {
        val t = ((value - from) / (to - from)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    private fun blend(from: Int, to: Int, t: Double): Int {
        fun channel(shift: Int) =
            ((from shr shift and BYTE) + ((to shr shift and BYTE) - (from shr shift and BYTE)) * t)
                .toInt()
        return (channel(RED) shl RED) or (channel(GREEN) shl GREEN) or channel(0)
    }

    private const val TILT = 0.4
    /** A light just under the horizon still falls from a little above it. */
    private const val MIN_ELEVATION = 0.08
    private const val HORIZON = 0.05
    private const val RISEN = 0.25
    private const val HIGH_SUN = 0.45
    private const val DAY_AMBIENT = 0.55
    private const val NIGHT_AMBIENT = 0.42
    private const val MOON_STRENGTH = 0.45
    private const val DAWN = 0xFFB070
    private const val NOON = 0xFFFFFF
    private const val MOON = 0x9AB0FF
    private const val DAY_TICKS = 24000L
    private const val QUARTER = 0.25
    private const val EASE_SHARE = 3.0
    private const val BYTE = 0xFF
    private const val RED = 16
    private const val GREEN = 8
}
