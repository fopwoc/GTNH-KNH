package io.github.fopwoc.mods.palimpsest.storage

/** Removes covered pixels that match the current tile while preserving explicit snapshots. */
internal object LayerNormalizer {
    data class Result(val layers: List<TileLayer>, val latest: Map<TileKey, ByteArray>)

    fun normalize(
        layers: List<TileLayer>,
        current: (TileKey) -> ByteArray?,
    ): Result {
        val latest = HashMap<TileKey, ByteArray>()
        val normalized = ArrayList<TileLayer>(layers.size)
        for (layer in layers) {
            val previous = latest[layer.key] ?: current(layer.key)?.copyOf()
            if (previous == null) {
                require(layer.coverage.all { it == -1L }) {
                    "First layer for ${layer.key} must cover all pixels"
                }
                latest[layer.key] = layer.colors.copyOf()
                normalized += layer
                continue
            }
            if (layer.isSnapshot) {
                latest[layer.key] = layer.colors.copyOf()
                normalized += layer
                continue
            }

            val coverage = LongArray(TileLayer.MASK_WORDS)
            val colors = ByteArray(layer.colors.size)
            var source = 0
            var kept = 0
            for (position in 0 until TileLayer.PIXELS) {
                val word = position ushr 6
                val bit = 1L shl (position and 63)
                if (layer.coverage[word] and bit == 0L) continue
                val color = layer.colors[source++]
                if (previous[position] == color) continue
                previous[position] = color
                coverage[word] = coverage[word] or bit
                colors[kept++] = color
            }
            latest[layer.key] = previous
            if (kept > 0) {
                normalized += TileLayer(layer.key, layer.epoch, coverage, colors.copyOf(kept))
            }
        }
        return Result(normalized, latest)
    }
}
