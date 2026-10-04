package io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree

import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource
import io.github.fopwoc.mods.palimpsest.tree.Sample

/**
 * A quadtree node over chunks, as in generation 2: four child refs (subtrees, or chunk leaves at
 * level 1) and one representative surface sample per child, so far zoom reads nodes and never a
 * chunk. Written in full or as a patch over the node it replaces; chains stop at [MAX_DEPTH].
 */
class TreeNode(val children: LongArray, val samples: LongArray, val depth: Int) {
    /** What a parent keeps for this subtree: the first present child's sample. */
    val representative: Long
        get() = samples.firstOrNull { it != Sample.NONE.packed } ?: Sample.NONE.packed

    companion object {
        const val MAX_DEPTH = 8
        private const val FULL = 0
        private const val PATCH = 1

        val EMPTY = TreeNode(LongArray(4), LongArray(4) { Sample.NONE.packed }, 0)

        fun encode(node: TreeNode, base: TreeNode?, baseRef: Long, origin: Long): Pair<ByteArray, Int> {
            val sink = ByteSink(48)
            if (base == null || base.depth >= MAX_DEPTH) {
                sink.byte(FULL)
                for (quarter in 0 until 4) {
                    Pack.writeRef(sink, node.children[quarter], origin)
                    if (node.children[quarter] != 0L) sink.fixed(node.samples[quarter], Sample.BYTES)
                }
                return sink.toByteArray() to 0
            }
            sink.byte(PATCH)
            Pack.writeRef(sink, baseRef, origin)
            var mask = 0
            for (quarter in 0 until 4) {
                if (node.children[quarter] != base.children[quarter]) mask = mask or (1 shl quarter)
                if (node.children[quarter] != 0L && node.samples[quarter] != base.samples[quarter])
                    mask = mask or (16 shl quarter)
            }
            sink.byte(mask)
            for (quarter in 0 until 4) {
                if (mask and (1 shl quarter) != 0) Pack.writeRef(sink, node.children[quarter], origin)
                if (mask and (16 shl quarter) != 0) sink.fixed(node.samples[quarter], Sample.BYTES)
            }
            return sink.toByteArray() to base.depth + 1
        }

        fun decode(bytes: ByteArray, origin: Long, resolve: (Long) -> TreeNode): TreeNode {
            val source = ByteSource(bytes, 1)
            if (bytes[0].toInt() == FULL) {
                val children = LongArray(4)
                val samples = LongArray(4) { Sample.NONE.packed }
                for (quarter in 0 until 4) {
                    children[quarter] = Pack.readRef(source, origin)
                    if (children[quarter] != 0L) samples[quarter] = source.fixed(Sample.BYTES)
                }
                return TreeNode(children, samples, 0)
            }
            val base = resolve(Pack.readRef(source, origin))
            val children = base.children.copyOf()
            val samples = base.samples.copyOf()
            val mask = source.byte()
            for (quarter in 0 until 4) {
                if (mask and (1 shl quarter) != 0) children[quarter] = Pack.readRef(source, origin)
                if (mask and (16 shl quarter) != 0) samples[quarter] = source.fixed(Sample.BYTES)
                if (children[quarter] == 0L) samples[quarter] = Sample.NONE.packed
            }
            return TreeNode(children, samples, base.depth + 1)
        }
    }
}
