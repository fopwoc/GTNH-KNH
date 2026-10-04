package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.BlockKinds
import io.github.fopwoc.palimpsest.db.BlockVocabulary

/**
 * [BlockKind] by block id raw, asked of the mod once per id per session and then read without
 * locks. Kinds are display, not truth: the index remembers the ones its surfaces were built with.
 */
internal class KindTable(private val vocabulary: BlockVocabulary, private val kinds: BlockKinds) {
    @Volatile private var table = ByteArray(0)

    fun kind(id: Int): BlockKind {
        val current = table
        if (id < current.size) return ENTRIES[current[id].toInt()]
        return grow(id)
    }

    /** Kinds of ids 0 until [size], as stored next to the index. */
    fun snapshot(size: Int): ByteArray {
        if (size > 0) kind(size - 1)
        return table.copyOf(size)
    }

    @Synchronized
    private fun grow(id: Int): BlockKind {
        val current = table
        if (id >= current.size) {
            val size = maxOf(vocabulary.size, id + 1)
            val next = current.copyOf(size)
            for (raw in current.size until size) next[raw] =
                (if (raw == 0) BlockKind.AIR else kinds.kind(vocabulary.identity(BlockId(raw))))
                    .ordinal
                    .toByte()
            table = next
        }
        return ENTRIES[table[id].toInt()]
    }

    private companion object {
        val ENTRIES = BlockKind.entries
    }
}
