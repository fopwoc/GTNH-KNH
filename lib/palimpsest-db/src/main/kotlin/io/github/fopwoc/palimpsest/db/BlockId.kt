package io.github.fopwoc.palimpsest.db

/** A block identity as this world's vocabulary numbers it; [AIR] is always 0. */
@JvmInline
value class BlockId(val raw: Int) {
    companion object {
        val AIR = BlockId(0)
    }
}
