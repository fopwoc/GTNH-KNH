package io.github.fopwoc.mods.palimpsest.client.map

/** Observes loaded chunks a few per tick; see the platform implementations. */
interface MapScanner {
    fun tick()

    /** Hands over anything buffered before the session closes. */
    fun flush()
}
