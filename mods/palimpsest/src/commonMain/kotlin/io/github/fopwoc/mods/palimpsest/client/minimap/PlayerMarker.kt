package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import kotlin.math.abs

/** The player's arrow, pointing up; draws turn it to the player's heading. */
internal object PlayerMarker {
    /** Image side in pixels; drawn at half that in GUI pixels, so it stays sharp at GUI scale 2. */
    const val SIZE = 16

    private const val SAMPLES = 4
    private const val TIP = 6.5
    private const val TAIL = -5.0
    private const val HALF_WIDTH = 4.5
    private const val NOTCH = 2.0
    private const val OUTLINE = 1.0

    val image: GpuImage by lazy(::draw)

    /** Clockwise turn of the up-pointing arrow that makes it face [yaw] on a north-up map. */
    fun rotation(yaw: Float): Float = yaw + HALF_TURN

    private fun draw(): GpuImage {
        val rgba = ByteArray(SIZE * SIZE * 4)
        val samples = SAMPLES * SAMPLES
        for (py in 0 until SIZE) for (px in 0 until SIZE) {
            var fill = 0
            var outline = 0
            for (sy in 0 until SAMPLES) for (sx in 0 until SAMPLES) {
                val across = px + (sx + 0.5) / SAMPLES - SIZE / 2.0
                val along = SIZE / 2.0 - (py + (sy + 0.5) / SAMPLES)
                if (inside(along, across, 0.0)) fill++
                else if (inside(along, across, OUTLINE)) outline++
            }
            if (fill + outline == 0) continue
            // White body over a dark outline, straight (not premultiplied) alpha.
            val light = (FILL_LIGHT * fill + OUTLINE_LIGHT * outline) / (fill + outline)
            val offset = (py * SIZE + px) * 4
            rgba[offset] = light.toByte()
            rgba[offset + 1] = light.toByte()
            rgba[offset + 2] = light.toByte()
            rgba[offset + 3] = ((fill + outline) * 255 / samples).toByte()
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
    private const val HALF_TURN = 180f
}
