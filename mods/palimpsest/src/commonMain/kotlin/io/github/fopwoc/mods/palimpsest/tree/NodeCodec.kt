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
        val sampleMask: Int = 15,
    ) {
        val isFull: Boolean
            get() = node != null

        fun mapBlocks(translate: (Int) -> Int): Decoded =
            if (node != null)
                Decoded(
                    node.mapBlocks(translate),
                    base,
                    maxEpoch,
                    quarters,
                    children,
                    samples,
                    sampleMask,
                )
            else
                Decoded(
                    null,
                    base,
                    maxEpoch,
                    quarters,
                    children,
                    LongArray(samples.size) { index ->
                        val sample = Sample(samples[index])
                        if (
                            Ref(children[index]).isNull ||
                                sampleMask and (1 shl quarters[index]) == 0
                        )
                            sample.packed
                        else
                            Sample(
                                    translate(sample.block),
                                    sample.height,
                                    sample.depth,
                                    sample.biome,
                                )
                                .packed
                    },
                    sampleMask,
                )

        fun apply(base: NodeRecord): NodeRecord {
            var node = base
            for ((index, quarter) in quarters.withIndex()) {
                node =
                    node.with(
                        quarter,
                        Ref(children[index]),
                        if (Ref(children[index]).isNull) Sample.NONE
                        else if (sampleMask and (1 shl quarter) != 0) Sample(samples[index])
                        else base.sample(quarter),
                        0,
                    )
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
        if (quarters.size !in 1..2) return false
        sink.byte(PATCH)
        refs.writeEpoch(sink, node.maxEpoch)
        refs.write(sink, baseRef)
        val childMask = quarters.fold(0) { mask, quarter -> mask or (1 shl quarter) }
        val sampleMask =
            quarters.fold(0) { mask, quarter ->
                if (node.sample(quarter) != base.sample(quarter)) mask or (1 shl quarter) else mask
            }
        sink.byte(childMask or (sampleMask shl 4))
        for (quarter in quarters) {
            refs.write(sink, node.child(quarter))
            if (!node.child(quarter).isNull && sampleMask and (1 shl quarter) != 0)
                Sample.write(sink, node.sample(quarter))
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
                val masks = source.byte()
                val childMask = masks and 15
                val sampleMask = masks ushr 4
                val count = childMask.countOneBits()
                if (count !in 1..2 || sampleMask and childMask != sampleMask)
                    throw CorruptTreeException("Invalid node patch masks $masks")
                val quarters =
                    (0 until NodeRecord.QUARTERS)
                        .filter { childMask and (1 shl it) != 0 }
                        .toIntArray()
                val children = LongArray(count)
                val samples = LongArray(count)
                for ((index, quarter) in quarters.withIndex()) {
                    children[index] = refs.read(source).packed
                    if (!Ref(children[index]).isNull && sampleMask and (1 shl quarter) != 0)
                        samples[index] = Sample.read(source).packed
                }
                Decoded(null, base, maxEpoch, quarters, children, samples, sampleMask)
            }
            else -> throw CorruptTreeException("Unknown node record kind $kind")
        }
}
