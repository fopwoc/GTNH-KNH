package io.github.fopwoc.mods.framework.world

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The 256 colors a map pixel byte can mean. Entry 0 is transparent; the rest are learned once from
 * the block colors of the game (median cut over every block color at every shade) and then frozen,
 * because the palette travels with the map data and every machine must read a byte the same way.
 * Colors that arrive later snap to their nearest entry.
 */
class WorldPalette private constructor(private val entries: IntArray) {
    private val nearestCache = ConcurrentHashMap<Int, Int>()

    init {
        require(entries.size == SIZE && entries[0] == ChunkColumns.TRANSPARENT)
    }

    val argb: IntArray
        get() = entries.copyOf()

    fun argb(index: Int): Int = entries[index and 255]

    /** Index of the closest entry; transparent maps to 0 and never to a color. */
    fun nearest(color: Int): Int {
        if (color == ChunkColumns.TRANSPARENT) return 0
        return nearestCache.getOrPut(color) {
            var best = 1
            var bestDistance = Int.MAX_VALUE
            for (index in 1 until SIZE) {
                val distance = distance(entries[index], color)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index
                }
            }
            best
        }
    }

    fun save(file: Path) {
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, ".palette-", ".tmp")
        try {
            DataOutputStream(Files.newOutputStream(temporary)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                entries.forEach(out::writeInt)
            }
            Files.move(
                temporary,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        const val SIZE = 256
        private const val MAGIC = 0x50414C54 // PALT
        private const val VERSION = 1
        /** Vanilla map brightness steps: slope down, flat, slope up. */
        val SHADES = intArrayOf(180, 220, 255)

        fun of(argb: IntArray): WorldPalette = WorldPalette(argb.copyOf())

        fun load(file: Path): WorldPalette {
            DataInputStream(Files.newInputStream(file)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                    throw IOException("Unsupported palette file $file")
                }
                return WorldPalette(IntArray(SIZE) { input.readInt() })
            }
        }

        fun loadOrCreate(file: Path, create: () -> WorldPalette): WorldPalette =
            if (Files.isRegularFile(file)) load(file) else create().also { it.save(file) }

        /**
         * Learns a palette from block colors. Every color at every shade is a candidate, bucketed
         * to 5 bits per channel and weighted by how many blocks share the bucket (capped, so a
         * thousand near-identical greys do not claim every entry while still anchoring the greys
         * they share); weighted median cut splits them into 255 boxes, each box becomes its
         * weighted average.
         */
        fun derive(blockColors: Iterable<Int>): WorldPalette {
            val weights = HashMap<Int, Int>()
            for (color in blockColors) {
                if (color == ChunkColumns.TRANSPARENT) continue
                for (factor in SHADES) weights.merge(bucket(shade(color, factor)), 1, Int::plus)
            }
            val candidates =
                weights
                    .map { (color, count) -> Weighted(color, minOf(count, MAX_WEIGHT)) }
                    .toMutableList()
            val boxes = medianCut(candidates, SIZE - 1)
            val entries = IntArray(SIZE)
            boxes.forEachIndexed { index, box -> entries[index + 1] = average(box) }
            // Fewer distinct colors than entries: fill the rest with the last color, harmless.
            for (index in boxes.size + 1 until SIZE) entries[index] = entries[boxes.size]
            return WorldPalette(entries)
        }

        private class Weighted(val color: Int, val weight: Int)

        private const val MAX_WEIGHT = 8

        /** 5 bits per channel: greys eight levels apart, common in block sets, stay distinct. */
        private fun bucket(color: Int): Int {
            val r = (color shr 16 and 0xF8) or 0x04
            val g = (color shr 8 and 0xF8) or 0x04
            val b = (color and 0xF8) or 0x04
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun medianCut(colors: MutableList<Weighted>, count: Int): List<List<Weighted>> {
            if (colors.isEmpty()) return emptyList()
            val boxes = ArrayList<MutableList<Weighted>>()
            boxes += colors
            while (boxes.size < count) {
                val widest = boxes.filter { it.size > 1 }.maxByOrNull(::range) ?: break
                val channel = widestChannel(widest)
                widest.sortBy { channelOf(it.color, channel) }
                val half = widest.sumOf { it.weight } / 2
                var seen = 0
                var middle = 0
                while (middle < widest.size - 1 && seen + widest[middle].weight <= half) {
                    seen += widest[middle].weight
                    middle++
                }
                if (middle == 0) middle = 1
                boxes -= widest
                boxes += widest.subList(0, middle).toMutableList()
                boxes += widest.subList(middle, widest.size).toMutableList()
            }
            return boxes
        }

        private fun range(box: List<Weighted>): Int =
            (0..2).maxOf { channel -> channelRange(box, channel) }

        private fun widestChannel(box: List<Weighted>): Int =
            (0..2).maxByOrNull { channel -> channelRange(box, channel) } ?: 0

        private fun channelRange(box: List<Weighted>, channel: Int): Int {
            var low = 255
            var high = 0
            for (entry in box) {
                val value = channelOf(entry.color, channel)
                if (value < low) low = value
                if (value > high) high = value
            }
            return high - low
        }

        private fun channelOf(color: Int, channel: Int): Int = color shr (16 - channel * 8) and 255

        private fun average(box: List<Weighted>): Int {
            var r = 0L
            var g = 0L
            var b = 0L
            var total = 0L
            for (entry in box) {
                val w = entry.weight.toLong()
                r += (entry.color shr 16 and 255) * w
                g += (entry.color shr 8 and 255) * w
                b += (entry.color and 255) * w
                total += w
            }
            return (0xFF shl 24) or
                ((r / total).toInt() shl 16) or
                ((g / total).toInt() shl 8) or
                (b / total).toInt()
        }

        fun shade(color: Int, factor: Int): Int {
            val r = (color shr 16 and 255) * factor / 255
            val g = (color shr 8 and 255) * factor / 255
            val b = (color and 255) * factor / 255
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun distance(a: Int, b: Int): Int {
            val dr = (a shr 16 and 255) - (b shr 16 and 255)
            val dg = (a shr 8 and 255) - (b shr 8 and 255)
            val db = (a and 255) - (b and 255)
            // Weighted like human sensitivity, green most.
            return 2 * dr * dr + 4 * dg * dg + 3 * db * db
        }
    }
}
