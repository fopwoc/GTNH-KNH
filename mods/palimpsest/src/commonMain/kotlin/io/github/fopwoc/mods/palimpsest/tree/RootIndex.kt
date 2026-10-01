package io.github.fopwoc.mods.palimpsest.tree

/** Immutable temporal pages: append copies at most 256 roots; readers never lock. */
class RootIndex private constructor(private val tail: Page?, val size: Int) {
    constructor(roots: List<RootRecord>) : this(pages(roots), roots.size)

    private class Page(
        val roots: Array<RootRecord>,
        val parent: Page?,
        val jumps: List<Page> = links(parent),
    )

    val latestEpoch: Long
        get() = tail?.roots?.last()?.epoch ?: -1

    val latest: RootRecord
        get() = tail?.roots?.last() ?: RootRecord.EMPTY

    /** The newest root at or before [epoch], including equal-epoch roots loaded from disk. */
    fun rootAt(epoch: Long): RootRecord {
        var page = tail ?: return RootRecord.EMPTY
        if (page.roots.first().epoch > epoch) {
            for (level in page.jumps.lastIndex downTo 0) {
                val previous = page.jumps.getOrNull(level)
                if (previous != null && previous.roots.first().epoch > epoch) page = previous
            }
            page = page.parent ?: return RootRecord.EMPTY
        }
        var low = 0
        var high = page.roots.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (page.roots[middle].epoch <= epoch) low = middle + 1 else high = middle
        }
        return if (low == 0) RootRecord.EMPTY else page.roots[low - 1]
    }

    fun with(root: RootRecord): RootIndex {
        require(root.epoch > latestEpoch) { "Epoch ${root.epoch} not after $latestEpoch" }
        val page =
            if (tail != null && tail.roots.size < CAPACITY)
                Page(tail.roots + root, tail.parent, tail.jumps)
            else Page(arrayOf(root), tail)
        return RootIndex(page, size + 1)
    }

    fun epochs(): LongArray {
        val result = LongArray(size)
        var page = tail
        var end = size
        while (page != null) {
            val start = end - page.roots.size
            for (at in page.roots.indices) result[start + at] = page.roots[at].epoch
            end = start
            page = page.parent
        }
        return result
    }

    companion object {
        private const val CAPACITY = 256

        private fun links(parent: Page?): List<Page> = buildList {
            parent?.let(::add)
            var level = 0
            while (level < size) {
                val next = get(level).jumps.getOrNull(level) ?: break
                add(next)
                level++
            }
        }

        private fun pages(roots: List<RootRecord>): Page? {
            val sorted = roots.sortedBy { it.epoch }
            var tail: Page? = null
            for (start in sorted.indices step CAPACITY) tail =
                Page(Array(minOf(CAPACITY, sorted.size - start)) { sorted[start + it] }, tail)
            return tail
        }
    }
}
