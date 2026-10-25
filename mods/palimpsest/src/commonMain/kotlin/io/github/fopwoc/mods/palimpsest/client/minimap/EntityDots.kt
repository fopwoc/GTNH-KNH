package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.client.EntityKind
import io.github.fopwoc.mods.framework.client.EntitySighting
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.palimpsest.client.motion.GlidingPoint
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Dots for items, mobs and other players near the player, on the minimap and the map screen.
 * Entities only move once a tick, so each dot glides after its entity like the map centre does
 * after the player. Nothing outlives the entities on screen: a dot is forgotten the frame its
 * entity is no longer seen.
 */
internal class EntityDots {
    private val glides = HashMap<Int, GlidingPoint>()

    /** [sightings] with positions glided [seconds] further; forgets entities not among them. */
    fun glide(sightings: List<EntitySighting>, seconds: Double): List<EntitySighting> {
        val seen = HashSet<Int>(sightings.size * 2)
        val glided = sightings.map { sighting ->
            seen += sighting.id
            val glide = glides[sighting.id]
            if (glide == null) {
                glides[sighting.id] = GlidingPoint(sighting.x, sighting.y, sighting.z)
                sighting
            } else {
                glide.follow(sighting.x, sighting.y, sighting.z, seconds)
                sighting.copy(x = glide.x, y = glide.y, z = glide.z)
            }
        }
        glides.keys.retainAll(seen)
        return glided
    }

    fun clear() = glides.clear()

    /**
     * A dot per entity the settings show that is loaded under [camera]'s map, glided [seconds]
     * further and turned with the map by [turn]; items under mobs under players, and faint when far
     * above or below [playerY].
     */
    fun draws(
        camera: MapCamera,
        turn: MapTurn,
        playerY: Double,
        seconds: Double,
    ): List<GpuImageDraw> =
        draws(
            radius(camera.width, camera.height, camera.pixelsPerBlock),
            camera.width,
            camera.height,
            playerY,
            seconds,
        ) { x, _, z ->
            val dx = (x - camera.centerX) * camera.pixelsPerBlock
            val dy = (z - camera.centerZ) * camera.pixelsPerBlock
            camera.width / 2.0 + turn.x(dx, dy) to camera.height / 2.0 + turn.y(dx, dy)
        }

    /** The same dots in the map's 3D view, standing at their own height. */
    fun draws(camera: IsoCamera, playerY: Double, seconds: Double): List<GpuImageDraw> =
        draws(
            radius(camera.width, camera.height, camera.pixelsPerBlock * IsoCamera.MIN_PITCH_SINE),
            camera.width,
            camera.height,
            playerY,
            seconds,
        ) { x, y, z ->
            camera.screenX(x, z) to camera.screenY(x, y, z)
        }

    private inline fun draws(
        radius: Double,
        width: Int,
        height: Int,
        playerY: Double,
        seconds: Double,
        project: (x: Double, y: Double, z: Double) -> Pair<Double, Double>,
    ): List<GpuImageDraw> =
        glide(sightings(radius), seconds)
            .sortedBy { it.kind.ordinal }
            .mapNotNull { sighting ->
                val size = size(sighting.kind)
                val (centerX, centerY) = project(sighting.x, sighting.y, sighting.z)
                val x = centerX - size / 2.0
                val y = centerY - size / 2.0
                if (x < -size || y < -size || x > width || y > height) return@mapNotNull null
                GpuImageDraw(
                    image(sighting.kind),
                    x.toFloat(),
                    y.toFloat(),
                    size,
                    size,
                    alpha = if (abs(sighting.y - playerY) > LEVEL_BLOCKS) FAINT else 1f,
                )
            }

    /** How far from the centre a canvas this big reaches, at any angle. */
    private fun radius(width: Int, height: Int, pixelsPerBlock: Double): Double =
        (hypot(width.toDouble(), height.toDouble()) / 2 / pixelsPerBlock + 1).coerceAtMost(
            MAX_ENTITY_RADIUS
        )

    /** The entities the settings ask for, loaded within [radius] blocks. */
    private fun sightings(radius: Double): List<EntitySighting> {
        val kinds = buildSet {
            if (PalimpsestConfig.minimapItems) add(EntityKind.ITEM)
            if (PalimpsestConfig.minimapMobs) addAll(listOf(EntityKind.HOSTILE, EntityKind.PASSIVE))
            if (PalimpsestConfig.minimapPlayers) add(EntityKind.PLAYER)
        }
        if (kinds.isEmpty()) return emptyList()
        return ClientBackend.current.entitiesNear(radius).filter { it.kind in kinds }
    }

    companion object {
        /** Entities are only loaded this near anyway; a bigger search just costs time. */
        private const val MAX_ENTITY_RADIUS = 512.0
        /** Height difference past which an entity's dot is faint: another floor or cave level. */
        private const val LEVEL_BLOCKS = 8.0
        private const val FAINT = 0.4f

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
