package io.github.fopwoc.mods.palimpsest.tree

/** A resolved window of tile-version addresses, without pixel data or version replay. */
internal class TileWindowIndex(private val refs: LongArray) {
    fun records(checkActive: () -> Unit, read: (Ref) -> TileRecord): Array<TileRecord?> =
        Array(refs.size) { offset ->
            checkActive()
            val ref = Ref(refs[offset])
            if (ref.isNull) null else read(ref)
        }
}
