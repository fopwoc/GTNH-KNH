package io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree

import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource

/**
 * One version of one chunk: refs to its 16 sections and to its 2D summary (the surface facts and
 * column biomes). Written either in full or as a patch over the previous version that names only
 * the changed slots, the 3D counterpart of a generation 2 patch node; chains stop at [MAX_DEPTH].
 */
class ChunkLeaf(val epoch: Long, val slots: LongArray, val previous: Long, val depth: Int) {
    val summary: Long
        get() = slots[SUMMARY]

    fun section(index: Int): Long = slots[index]

    companion object {
        const val SECTIONS = 16
        const val SUMMARY = SECTIONS
        const val SLOTS = SECTIONS + 1
        const val MAX_DEPTH = 8
        private const val FULL = 0
        private const val PATCH = 1

        /** Encodes [slots] as a version after [previous]; a patch while the chain allows it. */
        fun encode(epoch: Long, slots: LongArray, previous: ChunkLeaf?, previousRef: Long, origin: Long): Pair<ByteArray, Int> {
            val sink = ByteSink(64)
            if (previous == null || previous.depth >= MAX_DEPTH) {
                sink.byte(FULL)
                sink.varint(epoch)
                Pack.writeRef(sink, previousRef, origin)
                for (slot in slots) Pack.writeRef(sink, slot, origin)
                return sink.toByteArray() to 0
            }
            sink.byte(PATCH)
            sink.varint(epoch)
            Pack.writeRef(sink, previousRef, origin)
            var mask = 0
            for (slot in 0 until SLOTS) if (slots[slot] != previous.slots[slot]) mask = mask or (1 shl slot)
            sink.varint(mask)
            for (slot in 0 until SLOTS) if (mask and (1 shl slot) != 0) Pack.writeRef(sink, slots[slot], origin)
            return sink.toByteArray() to previous.depth + 1
        }

        fun decode(bytes: ByteArray, origin: Long, resolve: (Long) -> ChunkLeaf): ChunkLeaf {
            val source = ByteSource(bytes, 1)
            val epoch = source.varint()
            val previous = Pack.readRef(source, origin)
            if (bytes[0].toInt() == FULL)
                return ChunkLeaf(epoch, LongArray(SLOTS) { Pack.readRef(source, origin) }, previous, 0)
            val base = resolve(previous)
            val slots = base.slots.copyOf()
            val mask = source.varintInt()
            for (slot in 0 until SLOTS) if (mask and (1 shl slot) != 0) slots[slot] = Pack.readRef(source, origin)
            return ChunkLeaf(epoch, slots, previous, base.depth + 1)
        }
    }
}
