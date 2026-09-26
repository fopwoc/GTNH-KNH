package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The player's arrow, drawn once per heading step: canvas images cannot rotate, and at this size a
 * few dozen headings are all the arrow can show anyway.
 */
internal object PlayerMarker {
    /** Image side in pixels; drawn at half that in GUI pixels, so it stays sharp at GUI scale 2. */
    const val SIZE = 16

    private const val HEADINGS = 64
    private const val SAMPLES = 4
    private const val TIP = 6.5
    private const val TAIL = -5.0
    private const val HALF_WIDTH = 4.5
    private const val NOTCH = 2.0
    private const val OUTLINE = 1.0
    private val headings = arrayOfNulls<GpuImage>(HEADINGS)

    /** The arrow for [yaw] in Minecraft's convention, on a north-up map. */
    fun image(yaw: Float): GpuImage {
        val heading = Math.floorMod((yaw / 360f * HEADINGS).roundToInt(), HEADINGS)
        return headings[heading] ?: draw(heading * 360.0 / HEADINGS).also { headings[heading] = it }
    }

    // Yaw 0 looks south, which is down on a north-up map; 90 looks west, left.
    private fun draw(yawDegrees: Double): GpuImage {
        val radians = Math.toRadians(yawDegrees)
        val forwardX = -sin(radians)
        val forwardY = cos(radians)
        val rgba = ByteArray(SIZE * SIZE * 4)
        val samples = SAMPLES * SAMPLES
        for (py in 0 until SIZE) for (px in 0 until SIZE) {
            var fill = 0
            var outline = 0
            for (sy in 0 until SAMPLES) for (sx in 0 until SAMPLES) {
                val x = px + (sx + 0.5) / SAMPLES - SIZE / 2.0
                val y = py + (sy + 0.5) / SAMPLES - SIZE / 2.0
                val along = x * forwardX + y * forwardY
                val across = x * forwardY - y * forwardX
                if (inside(along, across, 0.0)) fill++
                else if (inside(along, across, OUTLINE)) outline++
            }
            val alpha = (fill + outline).toDouble() / samples
            if (alpha == 0.0) continue
            // White body over a dark outline, straight (not premultiplied) alpha.
            val light = (FILL_LIGHT * fill + OUTLINE_LIGHT * outline) / (fill + outline)
            val offset = (py * SIZE + px) * 4
            rgba[offset] = light.toByte()
            rgba[offset + 1] = light.toByte()
            rgba[offset + 2] = light.toByte()
            rgba[offset + 3] = (alpha * 255).roundToInt().toByte()
        }
        return GpuImage(SIZE, SIZE, rgba)
    }

    /** An arrowhead with a notched tail, [grow] pixels larger all round for the outline. */
    private fun inside(along: Double, across: Double, grow: Double): Boolean {
        val tip = TIP + grow
        val tail = TAIL - grow
        val halfWidth = HALF_WIDTH + grow
        if (along > tip || along < tail) return false
        if (abs(across) > (tip - along) / (tip - tail) * halfWidth) return false
        return along >= tail + NOTCH * (1 - abs(across) / halfWidth)
    }

    private const val FILL_LIGHT = 255
    private const val OUTLINE_LIGHT = 24
}
