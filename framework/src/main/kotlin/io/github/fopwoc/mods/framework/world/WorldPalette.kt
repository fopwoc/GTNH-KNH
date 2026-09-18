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
         * Learns a palette from block colors: every color at every shade is a candidate, median cut
         * splits them into 255 boxes, each box becomes its average.
         */
        fun derive(blockColors: Iterable<Int>): WorldPalette {
            val candidates =
                blockColors
                    .asSequence()
                    .filter { it != ChunkColumns.TRANSPARENT }
                    .flatMap { color -> SHADES.asSequence().map { shade(color, it) } }
                    .distinct()
                    .toMutableList()
            val boxes = medianCut(candidates, SIZE - 1)
            val entries = IntArray(SIZE)
            boxes.forEachIndexed { index, box -> entries[index + 1] = average(box) }
            // Fewer distinct colors than entries: fill the rest with the last color, harmless.
            for (index in boxes.size + 1 until SIZE) entries[index] = entries[boxes.size]
            return WorldPalette(entries)
        }

        fun shade(color: Int, factor: Int): Int {
            val r = (color shr 16 and 255) * factor / 255
            val g = (color shr 8 and 255) * factor / 255
            val b = (color and 255) * factor / 255
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun medianCut(colors: MutableList<Int>, count: Int): List<List<Int>> {
            if (colors.isEmpty()) return emptyList()
            val boxes = ArrayList<MutableList<Int>>()
            boxes += colors
            while (boxes.size < count) {
                val widest = boxes.filter { it.size > 1 }.maxByOrNull(::range) ?: break
                val channel = widestChannel(widest)
                widest.sortBy { channelOf(it, channel) }
                val middle = widest.size / 2
                boxes -= widest
                boxes += widest.subList(0, middle).toMutableList()
                boxes += widest.subList(middle, widest.size).toMutableList()
            }
            return boxes
        }

        private fun range(box: List<Int>): Int =
            (0..2).maxOf { channel -> channelRange(box, channel) }

        private fun widestChannel(box: List<Int>): Int =
            (0..2).maxByOrNull { channel -> channelRange(box, channel) } ?: 0

        private fun channelRange(box: List<Int>, channel: Int): Int {
            var low = 255
            var high = 0
            for (color in box) {
                val value = channelOf(color, channel)
                if (value < low) low = value
                if (value > high) high = value
            }
            return high - low
        }

        private fun channelOf(color: Int, channel: Int): Int = color shr (16 - channel * 8) and 255

        private fun average(box: List<Int>): Int {
            var r = 0L
            var g = 0L
            var b = 0L
            for (color in box) {
                r += color shr 16 and 255
                g += color shr 8 and 255
                b += color and 255
            }
            val n = box.size
            return (0xFF shl 24) or
                ((r / n).toInt() shl 16) or
                ((g / n).toInt() shl 8) or
                (b / n).toInt()
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
