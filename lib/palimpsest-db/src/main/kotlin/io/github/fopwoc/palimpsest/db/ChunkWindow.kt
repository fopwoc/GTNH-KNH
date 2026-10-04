package io.github.fopwoc.palimpsest.db

/** A rectangle of chunks: [width] × [height] starting at ([x0], [z0]). */
data class ChunkWindow(val x0: Int, val z0: Int, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Empty window ${width}x$height" }
    }

    operator fun contains(pos: ChunkPos): Boolean =
        pos.x - x0 in 0 until width && pos.z - z0 in 0 until height
}
