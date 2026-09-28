package io.github.fopwoc.mods.palimpsest.map

/** Pages and change notifications consumed by a live map view. */
interface MapPageSource {
    fun latest(key: MapPageKey, checkActive: () -> Unit = {}): MapPageRaster?

    fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster?

    fun addInvalidationListener(listener: (Collection<MapPageKey>) -> Unit)

    fun removeInvalidationListener(listener: (Collection<MapPageKey>) -> Unit)
}
