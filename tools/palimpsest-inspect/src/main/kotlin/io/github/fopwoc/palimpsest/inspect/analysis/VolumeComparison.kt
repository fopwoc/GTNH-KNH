package io.github.fopwoc.palimpsest.inspect.analysis

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.ChunkVolume

/**
 * How one chunk differs between two versions: blocks that went to air ([lost]), came from air
 * ([gained]) or turned into another block ([replaced]), each pair of identities that did, sections
 * that emptied or filled completely, and whether only the biomes moved.
 */
class VolumeComparison(old: ChunkVolume, new: ChunkVolume) {
    var lost = 0
        private set

    var gained = 0
        private set

    var replaced = 0
        private set

    /** From → to raw block ids → blocks. */
    val pairs = HashMap<Pair<BlockId, BlockId>, Int>()

    /** Section indexes, from the lowest of both versions, that went from blocks to none. */
    val emptied = ArrayList<Int>()

    /** Section indexes that went from none to blocks. */
    val filled = ArrayList<Int>()

    val biomesChanged = !biomes(old).contentEquals(biomes(new))

    val blocksChanged: Int
        get() = lost + gained + replaced

    /** Every changed block went to air: something was taken away and nothing put back. */
    val onlyLosses: Boolean
        get() = lost > 0 && gained == 0 && replaced == 0

    init {
        val minY = minOf(old.minY, new.minY)
        val maxY = maxOf(old.maxY, new.maxY)
        for (section in 0 until (maxY - minY + SECTION - 1) / SECTION) {
            var before = 0
            var after = 0
            val bottom = minY + section * SECTION
            for (y in bottom until bottom + SECTION) for (z in 0 until SECTION) for (x in
                0 until SECTION) {
                val a = old.block(x, y, z)
                val b = new.block(x, y, z)
                if (a != BlockId.AIR) before++
                if (b != BlockId.AIR) after++
                if (a == b) continue
                when {
                    b == BlockId.AIR -> lost++
                    a == BlockId.AIR -> gained++
                    else -> replaced++
                }
                pairs.merge(a to b, 1, Int::plus)
            }
            if (before > 0 && after == 0) emptied += section
            if (before == 0 && after > 0) filled += section
        }
    }

    companion object {
        private const val SECTION = 16

        /** A hash of every block and biome, to tell a version that comes back exactly. */
        fun fingerprint(volume: ChunkVolume): Int {
            var hash = biomes(volume).contentHashCode()
            for (y in volume.minY until volume.maxY) for (z in 0 until SECTION) for (x in
                0 until SECTION) hash = hash * 31 + volume.block(x, y, z).raw
            return hash
        }

        private fun biomes(volume: ChunkVolume): IntArray =
            when (val biomes = volume.biomes) {
                is Biomes.Columns -> biomes.values
            }
    }
}
