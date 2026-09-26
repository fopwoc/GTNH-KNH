package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.framework.client.EntityKind
import io.github.fopwoc.mods.framework.client.EntitySighting
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Dots for items, mobs and other players near the player. Entities only move once a tick, so each
 * dot glides after its entity like the map centre does after the player. Nothing outlives the
 * entities on screen: a dot is forgotten the frame its entity is no longer seen.
 */
internal class EntityDots {
    private class Glide(var x: Double, var y: Double, var z: Double)

    private val glides = HashMap<Int, Glide>()

    /** [sightings] with positions glided [seconds] further; forgets entities not among them. */
    fun glide(sightings: List<EntitySighting>, seconds: Double): List<EntitySighting> {
        val step = 1 - exp(-seconds / EASE_SECONDS)
        val seen = HashSet<Int>(sightings.size * 2)
        val glided = sightings.map { sighting ->
            seen += sighting.id
            val glide = glides[sighting.id]
            if (
                glide == null ||
                    hypot(sighting.x - glide.x, sighting.z - glide.z) > SNAP_BLOCKS ||
                    abs(sighting.y - glide.y) > SNAP_BLOCKS
            ) {
                glides[sighting.id] = Glide(sighting.x, sighting.y, sighting.z)
                sighting
            } else {
                glide.x += (sighting.x - glide.x) * step
                glide.y += (sighting.y - glide.y) * step
                glide.z += (sighting.z - glide.z) * step
                sighting.copy(x = glide.x, y = glide.y, z = glide.z)
            }
        }
        glides.keys.retainAll(seen)
        return glided
    }

    fun clear() = glides.clear()

    companion object {
        /** Same as the map centre's glide, so dots and map move together. */
        const val EASE_SECONDS = 0.05
        const val SNAP_BLOCKS = 32.0

        /** Side of the dot for [kind] in GUI pixels; players largest, items smallest. */
        fun size(kind: EntityKind): Float =
            when (kind) {
                EntityKind.ITEM -> 3f
                EntityKind.HOSTILE,
                EntityKind.PASSIVE -> 4f
                EntityKind.PLAYER -> 5f
            }

        fun image(kind: EntityKind): GpuImage = images.getValue(kind)

        private val images by lazy {
            EntityKind.entries.associateWith { kind ->
                disc(
                    when (kind) {
                        EntityKind.ITEM -> 0xFF3B3B
                        EntityKind.HOSTILE -> 0xFF8A1F
                        EntityKind.PASSIVE -> 0x5BE35B
                        EntityKind.PLAYER -> 0xFFFFFF
                    }
                )
            }
        }

        private const val SIZE = 8
        private const val SAMPLES = 4
        private const val FILL_RADIUS = 3.0
        private const val OUTLINE_RADIUS = 4.0
        private const val OUTLINE_LIGHT = 16

        /** A [rgb] disc with a dark rim, supersampled so it stays round when drawn small. */
        private fun disc(rgb: Int): GpuImage {
            val pixels = ByteArray(SIZE * SIZE * 4)
            val samples = SAMPLES * SAMPLES
            for (py in 0 until SIZE) for (px in 0 until SIZE) {
                var fill = 0
                var rim = 0
                for (sy in 0 until SAMPLES) for (sx in 0 until SAMPLES) {
                    val distance =
                        hypot(
                            px + (sx + 0.5) / SAMPLES - SIZE / 2.0,
                            py + (sy + 0.5) / SAMPLES - SIZE / 2.0,
                        )
                    if (distance <= FILL_RADIUS) fill++ else if (distance <= OUTLINE_RADIUS) rim++
                }
                if (fill + rim == 0) continue
                val offset = (py * SIZE + px) * 4
                for (channel in 0 until 3) {
                    val shift = 16 - channel * 8
                    val light = (rgb shr shift) and 0xFF
                    pixels[offset + channel] =
                        ((light * fill + OUTLINE_LIGHT * rim) / (fill + rim)).toByte()
                }
                pixels[offset + 3] = ((fill + rim) * 255 / samples).toByte()
            }
            return GpuImage(SIZE, SIZE, pixels)
        }
    }
}
