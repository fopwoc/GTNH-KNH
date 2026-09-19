package io.github.fopwoc.mods.palimpsest.tree

/**
 * How a record spells the things that depend on where it lives: refs and epochs. In memory a ref
 * names a segment by its runtime index; on disk a segment can only name segments by machine and
 * ordinal, so the segment layer supplies a coder that translates. Epochs are written relative to
 * [baseEpoch], the segment's base, so a commit's epoch costs two bytes instead of six.
 * [Direct] writes runtime indices and absolute epochs, for tests and in-memory use.
 */
interface RefCoder {
    val baseEpoch: Long

    fun write(sink: ByteSink, ref: Ref)

    fun read(source: ByteSource): Ref

    fun writeEpoch(sink: ByteSink, epoch: Long) = sink.signed(epoch - baseEpoch)

    fun readEpoch(source: ByteSource): Long = source.signed() + baseEpoch

    object Direct : RefCoder {
        override val baseEpoch: Long = 0

        override fun write(sink: ByteSink, ref: Ref) {
            if (ref.isNull) {
                sink.varint(0)
                return
            }
            sink.varint(ref.segment + 1)
            sink.varint(ref.offset)
        }

        override fun read(source: ByteSource): Ref {
            val segment = source.varintInt()
            if (segment == 0) return Ref.NULL
            return Ref(segment - 1, source.varintInt())
        }
    }
}
