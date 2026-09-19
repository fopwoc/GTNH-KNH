package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Where the map screen looks and when; owned by the screen so input handlers and UI share it.
 *
 * Input moves a target; [advance] eases the visible camera toward it every frame, so wheel notches,
 * trackpad deltas and key presses all land smoothly. A drag is the exception: it moves the view 1:1
 * and hands its speed over as a fling on release.
 */
class MapViewState(centerX: Double, centerZ: Double) {
    var centerX by mutableDoubleStateOf(centerX)
        private set

    var centerZ by mutableDoubleStateOf(centerZ)
        private set

    /** GUI pixels per block; 1 = one block per pixel, 1/16 = one chunk per pixel. */
    var pixelsPerBlock by mutableDoubleStateOf(1.0)
        private set

    private var targetCenterX = centerX
    private var targetCenterZ = centerZ
    private var targetLogZoom = 0.0
    private var zoomAnchorX = 0.0
    private var zoomAnchorY = 0.0
    private var flingX = 0.0
    private var flingZ = 0.0
    private var dragVelocityX = 0.0
    private var dragVelocityY = 0.0
    private var lastDragNanos = 0L
    private var lastFrameNanos = 0L

    fun camera(width: Int, height: Int): MapCamera =
        MapCamera(centerX, centerZ, pixelsPerBlock, width.coerceAtLeast(1), height.coerceAtLeast(1))

    /** Pointer drag by screen pixels: the map follows the cursor without easing. */
    fun dragBy(dx: Double, dy: Double, nowNanos: Long) {
        flingX = 0.0
        flingZ = 0.0
        centerX -= dx / pixelsPerBlock
        centerZ -= dy / pixelsPerBlock
        targetCenterX = centerX
        targetCenterZ = centerZ
        val dt = (nowNanos - lastDragNanos) / NANOS_PER_SECOND
        if (dt in MIN_DRAG_SAMPLE_SECONDS..DRAG_VELOCITY_MEMORY_SECONDS) {
            dragVelocityX =
                dragVelocityX * DRAG_VELOCITY_BLEND + (dx / dt) * (1 - DRAG_VELOCITY_BLEND)
            dragVelocityY =
                dragVelocityY * DRAG_VELOCITY_BLEND + (dy / dt) * (1 - DRAG_VELOCITY_BLEND)
        } else if (dt > DRAG_VELOCITY_MEMORY_SECONDS) {
            dragVelocityX = 0.0
            dragVelocityY = 0.0
        }
        lastDragNanos = nowNanos
    }

    /** Ends a drag; a cursor still moving when released turns its speed into a fling. */
    fun endDrag(nowNanos: Long) {
        val idle = (nowNanos - lastDragNanos) / NANOS_PER_SECOND
        if (idle <= DRAG_VELOCITY_MEMORY_SECONDS) {
            flingX = -dragVelocityX / pixelsPerBlock
            flingZ = -dragVelocityY / pixelsPerBlock
        }
        dragVelocityX = 0.0
        dragVelocityY = 0.0
    }

    /** Eased pan by screen pixels, for keys. */
    fun panBy(dx: Double, dy: Double) {
        targetCenterX -= dx / pixelsPerBlock
        targetCenterZ -= dy / pixelsPerBlock
    }

    fun lookAt(x: Double, z: Double) {
        flingX = 0.0
        flingZ = 0.0
        targetCenterX = x
        targetCenterZ = z
    }

    /**
     * Zooms by [steps] wheel notches (fractional for trackpads) around a screen point; the block
     * under that point stays put while the zoom eases in.
     */
    fun zoomBy(steps: Double, atX: Double, atY: Double) {
        targetLogZoom = (targetLogZoom + steps * ln(ZOOM_STEP)).coerceIn(MIN_LOG_ZOOM, MAX_LOG_ZOOM)
        zoomAnchorX = atX
        zoomAnchorY = atY
    }

    /** Moves the visible camera toward its targets; returns whether anything changed. */
    fun advance(frameNanos: Long, width: Int, height: Int): Boolean {
        val dt =
            if (lastFrameNanos == 0L) 0.0
            else ((frameNanos - lastFrameNanos) / NANOS_PER_SECOND).coerceIn(0.0, MAX_FRAME_SECONDS)
        lastFrameNanos = frameNanos
        if (dt == 0.0) return false

        var changed = false
        val before = pixelsPerBlock
        val logZoom = ln(before)
        if (abs(targetLogZoom - logZoom) > LOG_ZOOM_EPSILON) {
            val eased = logZoom + (targetLogZoom - logZoom) * ease(dt, ZOOM_SECONDS)
            val after =
                exp(if (abs(targetLogZoom - eased) > LOG_ZOOM_EPSILON) eased else targetLogZoom)
            // Keep the block under the anchor fixed by shifting the camera and its target alike.
            val shiftX = (zoomAnchorX - width / 2.0) * (1 / before - 1 / after)
            val shiftZ = (zoomAnchorY - height / 2.0) * (1 / before - 1 / after)
            pixelsPerBlock = after
            centerX += shiftX
            centerZ += shiftZ
            targetCenterX += shiftX
            targetCenterZ += shiftZ
            changed = true
        }

        if (flingX != 0.0 || flingZ != 0.0) {
            centerX += flingX * dt
            centerZ += flingZ * dt
            targetCenterX = centerX
            targetCenterZ = centerZ
            val decay = exp(-dt / FLING_SECONDS)
            flingX *= decay
            flingZ *= decay
            if (abs(flingX) * pixelsPerBlock < FLING_STOP_PIXELS_PER_SECOND) flingX = 0.0
            if (abs(flingZ) * pixelsPerBlock < FLING_STOP_PIXELS_PER_SECOND) flingZ = 0.0
            changed = true
        }

        val remainingX = targetCenterX - centerX
        val remainingZ = targetCenterZ - centerZ
        if (
            abs(remainingX) * pixelsPerBlock > PAN_EPSILON_PIXELS ||
                abs(remainingZ) * pixelsPerBlock > PAN_EPSILON_PIXELS
        ) {
            val k = ease(dt, PAN_SECONDS)
            centerX += remainingX * k
            centerZ += remainingZ * k
            changed = true
        } else if (remainingX != 0.0 || remainingZ != 0.0) {
            centerX = targetCenterX
            centerZ = targetCenterZ
            changed = true
        }
        return changed
    }

    private fun ease(dt: Double, seconds: Double): Double = 1 - exp(-dt / seconds)

    companion object {
        const val ZOOM_STEP = 1.25
        const val MIN_PIXELS_PER_BLOCK = 1.0 / 4096
        const val MAX_PIXELS_PER_BLOCK = 8.0
        private val MIN_LOG_ZOOM = ln(MIN_PIXELS_PER_BLOCK)
        private val MAX_LOG_ZOOM = ln(MAX_PIXELS_PER_BLOCK)
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val MAX_FRAME_SECONDS = 0.1
        private const val ZOOM_SECONDS = 0.08
        private const val PAN_SECONDS = 0.1
        private const val FLING_SECONDS = 0.3
        private const val FLING_STOP_PIXELS_PER_SECOND = 2.0
        private const val LOG_ZOOM_EPSILON = 1e-4
        private const val PAN_EPSILON_PIXELS = 0.05
        private const val MIN_DRAG_SAMPLE_SECONDS = 0.001
        private const val DRAG_VELOCITY_MEMORY_SECONDS = 0.1
        private const val DRAG_VELOCITY_BLEND = 0.5
    }
}
