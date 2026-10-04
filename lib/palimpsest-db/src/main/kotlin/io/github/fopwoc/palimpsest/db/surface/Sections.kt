package io.github.fopwoc.palimpsest.db.surface

/**
 * A chunk's sections bottom-up as the scan reads them: whether one holds anything is known up
 * front, its blocks may be decoded only when a column actually reaches it. Not thread-safe; one
 * scan, one instance.
 */
internal class Sections(
    val size: Int,
    private val has: (Int) -> Boolean,
    private val load: (Int) -> IntArray,
) {
    private val loaded = arrayOfNulls<IntArray>(size)

    fun has(index: Int): Boolean = has.invoke(index)

    /** The section's block id raws, or null for air. */
    operator fun get(index: Int): IntArray? {
        if (!has(index)) return null
        return loaded[index] ?: load(index).also { loaded[index] = it }
    }

    companion object {
        fun of(sections: Array<IntArray?>) =
            Sections(sections.size, { sections[it] != null }, { sections[it]!! })
    }
}
