package io.github.fopwoc.mods.palimpsest.tree

/**
 * One chunk seen from above at one moment: per pixel the block id, the height of that block, the
 * liquid depth above the floor (0 on land) and the biome. Facts, not colors; the renderer turns
 * them into pixels. Arrays are owned by the record and never handed out for mutation.
 */
class TileRecord(
    val epoch: Long,
    block: ShortArray,
    height: ByteArray,
    depth: ByteArray,
    biome: ShortArray,
) {
    private val block = block.copyOf()
    private val height = height.copyOf()
    private val depth = depth.copyOf()
    private val biome = biome.copyOf()

    init {
        require(epoch >= 0)
        require(
            block.size == PIXELS &&
                height.size == PIXELS &&
                depth.size == PIXELS &&
                biome.size == PIXELS
        )
    }

    fun block(position: Int): Int = block[position].toInt() and 0xFFFF

    fun height(position: Int): Int = height[position].toInt() and 0xFF

    fun depth(position: Int): Int = depth[position].toInt() and 0xFF

    fun biome(position: Int): Int = biome[position].toInt() and 0xFFFF

    fun sample(position: Int): Sample =
        Sample(block(position), height(position), depth(position), biome(position))

    /** The pixel a parent node keeps for this tile. */
    val sample: Sample
        get() = sample(CENTER)

    /** Channel values as ints, for coders and tests; a fresh array each call. */
    fun channel(channel: Channel): IntArray =
        when (channel) {
            Channel.BLOCK -> IntArray(PIXELS, ::block)
            Channel.HEIGHT -> IntArray(PIXELS, ::height)
            Channel.DEPTH -> IntArray(PIXELS, ::depth)
            Channel.BIOME -> IntArray(PIXELS, ::biome)
        }

    /** Positions whose facts differ from [other]; empty when the tiles look the same. */
    fun changedPositions(other: TileRecord): IntArray {
        val changed = IntArray(PIXELS)
        var count = 0
        for (position in 0 until PIXELS) {
            if (
                block[position] != other.block[position] ||
                    height[position] != other.height[position] ||
                    depth[position] != other.depth[position] ||
                    biome[position] != other.biome[position]
            )
                changed[count++] = position
        }
        return changed.copyOf(count)
    }

    /** Same facts in every pixel; the epoch does not count. */
    fun sameFacts(other: TileRecord): Boolean =
        block.contentEquals(other.block) &&
            height.contentEquals(other.height) &&
            depth.contentEquals(other.depth) &&
            biome.contentEquals(other.biome)

    /** A copy with the given epoch and some pixels replaced. */
    fun with(epoch: Long, positions: IntArray, values: Array<IntArray>): TileRecord {
        val block = block.copyOf()
        val height = height.copyOf()
        val depth = depth.copyOf()
        val biome = biome.copyOf()
        for ((index, position) in positions.withIndex()) {
            block[position] = values[Channel.BLOCK.ordinal][index].toShort()
            height[position] = values[Channel.HEIGHT.ordinal][index].toByte()
            depth[position] = values[Channel.DEPTH.ordinal][index].toByte()
            biome[position] = values[Channel.BIOME.ordinal][index].toShort()
        }
        return TileRecord(epoch, block, height, depth, biome)
    }

    /** A copy with the same facts at another epoch. */
    fun withEpoch(epoch: Long): TileRecord = TileRecord(epoch, block, height, depth, biome)

    /** A copy with every block id passed through [translate]. */
    fun mapBlocks(translate: (Int) -> Int): TileRecord =
        TileRecord(
            epoch,
            ShortArray(PIXELS) { translate(block(it)).toShort() },
            height,
            depth,
            biome,
        )

    override fun equals(other: Any?): Boolean =
        other is TileRecord && epoch == other.epoch && sameFacts(other)

    override fun hashCode(): Int {
        var hash = epoch.hashCode()
        hash = hash * 31 + block.contentHashCode()
        hash = hash * 31 + height.contentHashCode()
        hash = hash * 31 + depth.contentHashCode()
        return hash * 31 + biome.contentHashCode()
    }

    /** The four per-pixel channels, in record order, with their width in bytes. */
    enum class Channel(val bytes: Int) {
        BLOCK(2),
        HEIGHT(1),
        DEPTH(1),
        BIOME(2);

        val maxValue: Int
            get() = (1 shl (bytes * 8)) - 1
    }

    companion object {
        const val SIDE = 16
        const val PIXELS = SIDE * SIDE
        const val CENTER = (SIDE / 2) * SIDE + SIDE / 2

        fun build(
            epoch: Long,
            block: (Int) -> Int,
            height: (Int) -> Int = { 0 },
            depth: (Int) -> Int = { 0 },
            biome: (Int) -> Int = { 0 },
        ): TileRecord =
            TileRecord(
                epoch,
                ShortArray(PIXELS) { block(it).toShort() },
                ByteArray(PIXELS) { height(it).toByte() },
                ByteArray(PIXELS) { depth(it).toByte() },
                ShortArray(PIXELS) { biome(it).toShort() },
            )

        fun solid(
            epoch: Long,
            block: Int,
            height: Int = 0,
            depth: Int = 0,
            biome: Int = 0,
        ): TileRecord = build(epoch, { block }, { height }, { depth }, { biome })
    }
}
