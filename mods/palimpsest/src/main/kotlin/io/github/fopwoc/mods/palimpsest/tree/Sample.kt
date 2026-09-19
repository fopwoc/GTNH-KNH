package io.github.fopwoc.mods.palimpsest.tree

/**
 * One pixel's facts — block id, height, liquid depth, biome — packed in a Long. This is what a node
 * keeps per child and what a page samples at far zoom; [NONE] marks a child that does not exist.
 */
@JvmInline
value class Sample(val packed: Long) {
    constructor(
        block: Int,
        height: Int,
        depth: Int,
        biome: Int,
    ) : this(
        ((block.toLong() and 0xFFFF) shl 32) or
            ((height.toLong() and 0xFF) shl 24) or
            ((depth.toLong() and 0xFF) shl 16) or
            (biome.toLong() and 0xFFFF)
    )

    val block: Int
        get() = (packed ushr 32).toInt() and 0xFFFF

    val height: Int
        get() = (packed ushr 24).toInt() and 0xFF

    val depth: Int
        get() = (packed ushr 16).toInt() and 0xFF

    val biome: Int
        get() = packed.toInt() and 0xFFFF

    val isNone: Boolean
        get() = packed == NONE.packed

    override fun toString(): String =
        if (isNone) "Sample(none)"
        else "Sample(block=$block height=$height depth=$depth biome=$biome)"

    companion object {
        /** Bit 63 set: no valid sample has it, the fields only use the low 48 bits. */
        val NONE = Sample(Long.MIN_VALUE)
        const val BYTES = 6

        fun write(sink: ByteSink, sample: Sample) = sink.fixed(sample.packed, BYTES)

        fun read(source: ByteSource): Sample = Sample(source.fixed(BYTES))
    }
}
