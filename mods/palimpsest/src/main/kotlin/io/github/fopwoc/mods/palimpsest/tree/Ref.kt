package io.github.fopwoc.mods.palimpsest.tree

/**
 * Address of a record: which segment and the byte offset inside it, packed in one Long so arrays of
 * refs stay primitive. Segment ids are assigned by the [SegmentSet]; a ref never changes when a
 * segment is sealed and renamed.
 */
@JvmInline
value class Ref(val packed: Long) {
    constructor(
        segment: Int,
        offset: Int,
    ) : this((segment.toLong() shl 32) or (offset.toLong() and 0xFFFFFFFFL))

    val segment: Int
        get() = (packed ushr 32).toInt()

    val offset: Int
        get() = packed.toInt()

    val isNull: Boolean
        get() = packed == NULL.packed

    override fun toString(): String = if (isNull) "Ref(null)" else "Ref($segment@$offset)"

    companion object {
        val NULL = Ref(-1L)
    }
}
