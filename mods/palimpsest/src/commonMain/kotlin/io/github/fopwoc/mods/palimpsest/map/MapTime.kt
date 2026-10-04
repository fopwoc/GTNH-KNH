package io.github.fopwoc.mods.palimpsest.map

/**
 * What moment the map shows: the live view, or history as of a world [tick]; [observedAt] is when
 * that moment was committed, in epoch millis, for labels.
 */
sealed interface MapTime {
    data object Live : MapTime

    data class At(val tick: Long, val observedAt: Long) : MapTime {
        init {
            require(tick >= 0)
        }
    }
}
