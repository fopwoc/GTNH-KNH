package io.github.fopwoc.mods.palimpsest.render.iso

/**
 * The large shape of the ground from a grid of heights, for shading that should show hills and not
 * blocks: bumps and gaps up to [featureRadius] cells either way — trees, and the gaps between them
 * — are shaved off and filled in, an opening and a closing; then what remains is averaged over
 * [blurRadius] cells either way, so terraces of single blocks become an even slope. Unseen cells
 * are NaN, stay so and count for nothing.
 */
internal object GroundSmoothing {
    private enum class Pick {
        LOWEST,
        HIGHEST,
        MEAN,
    }

    /** Smooths [heights], [width] × [height] row by row, in place. */
    fun smooth(heights: FloatArray, width: Int, height: Int, featureRadius: Int, blurRadius: Int) {
        require(heights.size == width * height)
        if (featureRadius > 0) {
            pass(heights, width, height, featureRadius, Pick.LOWEST)
            pass(heights, width, height, featureRadius, Pick.HIGHEST)
            pass(heights, width, height, featureRadius, Pick.HIGHEST)
            pass(heights, width, height, featureRadius, Pick.LOWEST)
        }
        if (blurRadius > 0) pass(heights, width, height, blurRadius, Pick.MEAN)
    }

    /** [pick] over [radius] cells either way, first along rows, then along columns. */
    private fun pass(heights: FloatArray, width: Int, height: Int, radius: Int, pick: Pick) {
        val line = FloatArray(maxOf(width, height))
        for (row in 0 until height) {
            for (column in 0 until width) line[column] = heights[row * width + column]
            for (column in 0 until width) if (!line[column].isNaN())
                heights[row * width + column] = window(line, width, column, radius, pick)
        }
        for (column in 0 until width) {
            for (row in 0 until height) line[row] = heights[row * width + column]
            for (row in 0 until height) if (!line[row].isNaN())
                heights[row * width + column] = window(line, height, row, radius, pick)
        }
    }

    private fun window(line: FloatArray, size: Int, at: Int, radius: Int, pick: Pick): Float {
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE
        var sum = 0f
        var count = 0
        for (k in maxOf(0, at - radius)..minOf(size - 1, at + radius)) {
            val value = line[k]
            if (value.isNaN()) continue
            lowest = minOf(lowest, value)
            highest = maxOf(highest, value)
            sum += value
            count++
        }
        return when (pick) {
            Pick.LOWEST -> lowest
            Pick.HIGHEST -> highest
            Pick.MEAN -> sum / count
        }
    }
}
