package io.github.fopwoc.mods.palimpsest.tree

/**
 * An internal square of the quadtree: for each of its four quarters (x-major: 0 = north-west, 1 =
 * north-east, 2 = south-west, 3 = south-east) where the child lives and the one pixel that stands
 * for it, plus the newest epoch anywhere below, so time-range queries can skip whole subtrees.
 * Immutable; a changed child means a new node. [patchDepth] counts how many patch records lie
 * between this node and a full one on disk; the codec bounds it.
 */
class NodeRecord(
    children: LongArray,
    samples: LongArray,
    val maxEpoch: Long,
    val patchDepth: Int = 0,
) {
    private val children = children.copyOf()
    private val samples = samples.copyOf()

    init {
        require(
            children.size == QUARTERS &&
                samples.size == QUARTERS &&
                maxEpoch >= 0 &&
                patchDepth >= 0
        )
        for (quarter in 0 until QUARTERS) {
            require(Ref(children[quarter]).isNull == Sample(samples[quarter]).isNone)
        }
    }

    fun child(quarter: Int): Ref = Ref(children[quarter])

    fun sample(quarter: Int): Sample = Sample(samples[quarter])

    /** The pixel this node's parent keeps for it: the first present quarter's sample. */
    val sample: Sample
        get() {
            for (quarter in 0 until QUARTERS) if (!child(quarter).isNull) return sample(quarter)
            return Sample.NONE
        }

    val isEmpty: Boolean
        get() = (0 until QUARTERS).all { child(it).isNull }

    /** The single present quarter, or -1 when there are none or several. */
    val onlyQuarter: Int
        get() {
            var found = -1
            for (quarter in 0 until QUARTERS) {
                if (child(quarter).isNull) continue
                if (found >= 0) return -1
                found = quarter
            }
            return found
        }

    fun with(quarter: Int, child: Ref, sample: Sample, epoch: Long): NodeRecord {
        require(child.isNull == sample.isNone)
        val children = children.copyOf().also { it[quarter] = child.packed }
        val samples = samples.copyOf().also { it[quarter] = sample.packed }
        return NodeRecord(children, samples, maxOf(maxEpoch, epoch), patchDepth)
    }

    fun withPatchDepth(depth: Int): NodeRecord = NodeRecord(children, samples, maxEpoch, depth)

    /** Quarters whose child or sample differ from [base]. */
    fun changedQuarters(base: NodeRecord): IntArray =
        (0 until QUARTERS)
            .filter { children[it] != base.children[it] || samples[it] != base.samples[it] }
            .toIntArray()

    /** The same node with every sample's block id passed through [translate]. */
    fun mapBlocks(translate: (Int) -> Int): NodeRecord =
        NodeRecord(
            children,
            LongArray(QUARTERS) { quarter ->
                val sample = sample(quarter)
                if (sample.isNone) sample.packed
                else
                    Sample(translate(sample.block), sample.height, sample.depth, sample.biome)
                        .packed
            },
            maxEpoch,
            patchDepth,
        )

    override fun equals(other: Any?): Boolean =
        other is NodeRecord &&
            maxEpoch == other.maxEpoch &&
            children.contentEquals(other.children) &&
            samples.contentEquals(other.samples)

    override fun hashCode(): Int =
        (children.contentHashCode() * 31 + samples.contentHashCode()) * 31 + maxEpoch.hashCode()

    companion object {
        const val QUARTERS = 4

        val EMPTY =
            NodeRecord(
                LongArray(QUARTERS) { Ref.NULL.packed },
                LongArray(QUARTERS) { Sample.NONE.packed },
                0,
            )

        /** A node whose only child is [child] in [quarter]. */
        fun single(quarter: Int, child: Ref, sample: Sample, epoch: Long): NodeRecord =
            EMPTY.with(quarter, child, sample, epoch)

        /**
         * Quarter of a child at (x, z) inside a node at [level] whose children are at level - 1.
         */
        fun quarter(x: Int, z: Int, level: Int): Int {
            val bit = level - 1
            return ((z ushr bit) and 1) * 2 + ((x ushr bit) and 1)
        }
    }
}
