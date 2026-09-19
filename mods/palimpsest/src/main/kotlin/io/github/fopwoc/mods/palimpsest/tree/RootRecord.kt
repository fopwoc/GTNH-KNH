package io.github.fopwoc.mods.palimpsest.tree

/**
 * One commit: the epoch and the top node of its tree. The top node sits at [level] in square
 * ([x], [z]) of that level — the smallest square that holds everything seen so far — so the
 * empty single-child levels above the explored world are never written; the tree grows a level
 * only when an observation lands outside the square.
 */
class RootRecord(val epoch: Long, val level: Int, val x: Int, val z: Int, val ref: Ref) {
    init {
        require(epoch >= 0 && level in 0..MapTree.LEVELS && x >= 0 && z >= 0)
        require(ref.isNull == (level == MapTree.LEVELS && x == 0 && z == 0) || !ref.isNull)
    }

    companion object {
        /** No observations yet: the whole-world square with nothing in it. */
        val EMPTY = RootRecord(0, MapTree.LEVELS, 0, 0, Ref.NULL)

        fun write(sink: ByteSink, root: RootRecord, refs: RefCoder) {
            refs.writeEpoch(sink, root.epoch)
            sink.byte(root.level)
            sink.varint(root.x)
            sink.varint(root.z)
            refs.write(sink, root.ref)
        }

        fun read(source: ByteSource, refs: RefCoder): RootRecord {
            val epoch = refs.readEpoch(source)
            val level = source.byte()
            if (level > MapTree.LEVELS) throw CorruptTreeException("Root level $level")
            return RootRecord(epoch, level, source.varintInt(), source.varintInt(), refs.read(source))
        }
    }
}
