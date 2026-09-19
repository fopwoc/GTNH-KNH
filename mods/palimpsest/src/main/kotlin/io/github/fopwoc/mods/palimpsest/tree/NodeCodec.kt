package io.github.fopwoc.mods.palimpsest.tree

/**
 * Bytes of a [NodeRecord]. A **full** node lists all four quarters; a **patch** names a base node
 * and only the quarters that differ from it — the shape of almost every node a commit rewrites,
 * since a commit usually changes one child of four. Reading a patch needs its base, so [decode]
 * returns what it found and [Decoded.base] says what else to fetch; a full node is written once
 * [MAX_PATCH_DEPTH] patches have stacked up.
 */
object NodeCodec {
    private const val FULL = 1
    private const val PATCH = 2
    const val MAX_PATCH_DEPTH = 8

    /** A full node, or a patch that still needs its [base] applied. */
    class Decoded(
        val node: NodeRecord?,
        val base: Ref,
        val maxEpoch: Long,
        val quarters: IntArray,
        val children: LongArray,
        val samples: LongArray,
    ) {
        val isFull: Boolean
            get() = node != null

        fun apply(base: NodeRecord): NodeRecord {
            var node = base
            for ((index, quarter) in quarters.withIndex()) {
                node = node.with(quarter, Ref(children[index]), Sample(samples[index]), 0)
            }
            return NodeRecord(
                LongArray(NodeRecord.QUARTERS) { node.child(it).packed },
                LongArray(NodeRecord.QUARTERS) { node.sample(it).packed },
                maxEpoch,
                base.patchDepth + 1,
            )
        }
    }

    fun encodeFull(sink: ByteSink, node: NodeRecord, refs: RefCoder) {
        sink.byte(FULL)
        refs.writeEpoch(sink, node.maxEpoch)
        for (quarter in 0 until NodeRecord.QUARTERS) writeQuarter(sink, node, quarter, refs)
    }

    /**
     * Encodes [node] against [base] when that is allowed and smaller; returns false to fall back to
     * full.
     */
    fun encodePatch(
        sink: ByteSink,
        node: NodeRecord,
        base: NodeRecord,
        baseRef: Ref,
        refs: RefCoder,
    ): Boolean {
        if (base.patchDepth >= MAX_PATCH_DEPTH) return false
        val quarters = node.changedQuarters(base)
        if (quarters.size > 2) return false
        sink.byte(PATCH)
        refs.writeEpoch(sink, node.maxEpoch)
        refs.write(sink, baseRef)
        sink.byte(quarters.size)
        for (quarter in quarters) {
            sink.byte(quarter)
            writeQuarter(sink, node, quarter, refs)
        }
        return true
    }

    private fun writeQuarter(sink: ByteSink, node: NodeRecord, quarter: Int, refs: RefCoder) {
        val child = node.child(quarter)
        refs.write(sink, child)
        if (!child.isNull) Sample.write(sink, node.sample(quarter))
    }

    private fun readQuarter(source: ByteSource, refs: RefCoder): Pair<Long, Long> {
        val child = refs.read(source)
        val sample = if (child.isNull) Sample.NONE.packed else Sample.read(source).packed
        return child.packed to sample
    }

    fun decode(source: ByteSource, refs: RefCoder): Decoded =
        when (val kind = source.byte()) {
            FULL -> {
                val maxEpoch = refs.readEpoch(source)
                val children = LongArray(NodeRecord.QUARTERS)
                val samples = LongArray(NodeRecord.QUARTERS)
                for (quarter in 0 until NodeRecord.QUARTERS) {
                    val (child, sample) = readQuarter(source, refs)
                    children[quarter] = child
                    samples[quarter] = sample
                }
                Decoded(
                    NodeRecord(children, samples, maxEpoch),
                    Ref.NULL,
                    maxEpoch,
                    IntArray(0),
                    LongArray(0),
                    LongArray(0),
                )
            }
            PATCH -> {
                val maxEpoch = refs.readEpoch(source)
                val base = refs.read(source)
                if (base.isNull) throw CorruptTreeException("Patch node without a base")
                val count = source.byte()
                if (count !in 1..2) throw CorruptTreeException("Patch node with $count quarters")
                val quarters = IntArray(count)
                val children = LongArray(count)
                val samples = LongArray(count)
                for (index in 0 until count) {
                    quarters[index] =
                        source.byte().also {
                            if (it >= NodeRecord.QUARTERS) throw CorruptTreeException("Quarter $it")
                        }
                    val (child, sample) = readQuarter(source, refs)
                    children[index] = child
                    samples[index] = sample
                }
                Decoded(null, base, maxEpoch, quarters, children, samples)
            }
            else -> throw CorruptTreeException("Unknown node record kind $kind")
        }
}
