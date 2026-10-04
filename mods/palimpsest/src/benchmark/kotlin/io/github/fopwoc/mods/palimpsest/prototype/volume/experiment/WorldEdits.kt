package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.BlockKind
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.tree.TileKey

/**
 * Block edits in world coordinates over a loaded area, buffered per chunk and applied together, so
 * one simulated minute becomes one set of changed chunk volumes. Edits outside the area are
 * dropped.
 */
class WorldEdits(
    private val world: MutableMap<TileKey, ChunkVolume>,
    private val vocabulary: Vocabulary,
) {
    private val pending = LinkedHashMap<TileKey, MutableList<ChunkVolume.Editor.() -> Unit>>()

    fun block(x: Int, y: Int, z: Int): Int =
        world[TileKey(x shr 4, z shr 4)]?.block(x and 15, y, z and 15) ?: 0

    /** The highest block a player could stand on or build from, or -1 outside the area. */
    fun ground(x: Int, z: Int): Int {
        val volume = world[TileKey(x shr 4, z shr 4)] ?: return -1
        for (y in ChunkVolume.HEIGHT - 1 downTo 0) {
            val kind = vocabulary.kind(volume.block(x and 15, y, z and 15))
            if (kind == BlockKind.SOLID || kind == BlockKind.WATER) return y
        }
        return -1
    }

    fun set(x: Int, y: Int, z: Int, id: Int) {
        if (y !in 0 until ChunkVolume.HEIGHT) return
        val key = TileKey(x shr 4, z shr 4)
        if (key !in world) return
        pending.getOrPut(key, ::mutableListOf) += { set(x and 15, y, z and 15, id) }
    }

    fun biome(x: Int, z: Int, biome: Int) {
        val key = TileKey(x shr 4, z shr 4)
        if (key !in world) return
        pending.getOrPut(key, ::mutableListOf) += { biome(x and 15, z and 15, biome) }
    }

    /** Applies everything buffered and returns the chunks it touched, as they are now. */
    fun flush(): Map<TileKey, ChunkVolume> {
        val changed = pending.mapValues { (key, edits) ->
            world.getValue(key).edit { edits.forEach { it() } }.also { world[key] = it }
        }
        pending.clear()
        return changed
    }
}
