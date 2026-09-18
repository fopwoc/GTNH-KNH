package io.github.fopwoc.mods.palimpsest.map

/** What moment the map shows: the live view, or history as of an epoch. */
sealed interface MapTime {
    data object Live : MapTime

    data class At(val epoch: Long) : MapTime {
        init {
            require(epoch >= 0)
        }
    }
}
