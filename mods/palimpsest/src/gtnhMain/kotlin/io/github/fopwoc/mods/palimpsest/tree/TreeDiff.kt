package io.github.fopwoc.mods.palimpsest.tree

/** Squares at [level] inside a window of the map, and what to do with a differing one. */
internal class DiffWindow(
    val level: Int,
    val x0: Int,
    val z0: Int,
    val side: Int,
    val full: () -> Boolean = { false },
    val mark: (x: Int, z: Int) -> Unit,
)

/**
 * Finds the squares of a window that differ between two roots of a [MapTree], descending only into
 * subtrees the roots do not share.
 */
internal class TreeDiff(private val tree: MapTree, private val window: DiffWindow) {
    fun run(epochA: Long, epochB: Long) {
        val a = tree.roots.rootAt(epochA)
        val b = tree.roots.rootAt(epochB)
        val top = maxOf(a.level, b.level, window.level)
        diff(Placed.of(a, top), Placed.of(b, top), top)
    }

    /**
     * A node ref seen at a level at or above its own: above it, a virtual square with one child.
     */
    private class Placed(val ref: Ref, val level: Int, val x: Int, val z: Int) {
        /** The child in [quarter] of the square containing this node at [at], or null. */
        fun childAt(at: Int, quarter: Int, tree: MapTree): Placed? {
            if (at > level) {
                val x = this.x ushr (at - 1 - level)
                val z = this.z ushr (at - 1 - level)
                return if (NodeRecord.quarter(x, z, 1) == quarter) this else null
            }
            val child = tree.node(ref).child(quarter)
            return if (child.isNull) null
            else Placed(child, level - 1, x * 2 + (quarter and 1), z * 2 + (quarter shr 1))
        }

        fun squareAt(at: Int): Pair<Int, Int> = (x ushr (at - level)) to (z ushr (at - level))

        companion object {
            fun of(root: RootRecord, top: Int): Placed? =
                if (root.ref.isNull) null
                else
                    Placed(root.ref, root.level, root.x, root.z).also { require(root.level <= top) }
        }
    }

    private fun inWindow(x: Int, z: Int, level: Int): Boolean =
        tree.intersects(x, z, level, window.level, window.x0, window.z0, window.side)

    private fun diff(a: Placed?, b: Placed?, level: Int) {
        if (a == null && b == null || window.full()) return
        if (a != null && b != null && a.ref == b.ref && a.level == b.level) return
        val (x, z) = (a ?: checkNotNull(b)).squareAt(level)
        if (!inWindow(x, z, level)) return
        if (level == window.level) {
            window.mark(x, z)
            return
        }
        if (a == null || b == null) {
            markPresent(a ?: checkNotNull(b), level)
            return
        }
        for (quarter in 0 until NodeRecord.QUARTERS) {
            diff(a.childAt(level, quarter, tree), b.childAt(level, quarter, tree), level - 1)
        }
    }

    /** One side has nothing here: every square present on the other side changed. */
    private fun markPresent(placed: Placed, level: Int) {
        if (window.full()) return
        val (x, z) = placed.squareAt(level)
        if (!inWindow(x, z, level)) return
        if (level == window.level) {
            window.mark(x, z)
            return
        }
        for (quarter in 0 until NodeRecord.QUARTERS) {
            val child = placed.childAt(level, quarter, tree) ?: continue
            markPresent(child, level - 1)
        }
    }
}
