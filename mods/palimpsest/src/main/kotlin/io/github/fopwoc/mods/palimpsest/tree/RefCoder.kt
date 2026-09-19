package io.github.fopwoc.mods.palimpsest.tree

/**
 * How a [Ref] is spelled inside a record. In memory a ref names a segment by its runtime index; on
 * disk a segment can only name segments by machine and ordinal, so the segment layer supplies a
 * coder that translates. [Direct] writes the runtime index as-is, for tests and in-memory use.
 */
interface RefCoder {
    fun write(sink: ByteSink, ref: Ref)

    fun read(source: ByteSource): Ref

    object Direct : RefCoder {
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
