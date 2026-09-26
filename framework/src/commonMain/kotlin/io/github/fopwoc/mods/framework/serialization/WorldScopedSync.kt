package io.github.fopwoc.mods.framework.serialization

import io.github.fopwoc.mods.framework.client.ClientBackend

/**
 * Drives a [WorldScopedJsonStore] from the client tick: loads when the world/server changes
 * (`onLoaded(null)` when there is none), saves [debounceTicks] after [markDirty], and flushes on a
 * context change. Call [tick] every client tick and [flush] on disconnect; physical client only.
 */
class WorldScopedSync<T : Any>(
    private val store: WorldScopedJsonStore<T>,
    private val debounceTicks: Int = 0,
    private val onLoaded: (T?) -> Unit,
    private val snapshot: () -> T,
) {
    private var loadedContextId: String? = null
    private var dirtySinceTick: Int? = null
    private var tickCounter = 0

    val contextId: String?
        get() = loadedContextId

    fun markDirty() {
        if (dirtySinceTick == null) {
            dirtySinceTick = tickCounter
        }
    }

    fun tick() {
        tickCounter += 1
        val contextId = ClientBackend.current.currentWorldId
        if (contextId != loadedContextId) {
            flush()
            loadedContextId = contextId
            dirtySinceTick = null
            onLoaded(contextId?.let(store::load))
            return
        }
        val since = dirtySinceTick ?: return
        if (tickCounter - since >= debounceTicks) {
            flush()
        }
    }

    /** Writes now if anything changed since the last save. */
    fun flush() {
        val contextId = loadedContextId ?: return
        if (dirtySinceTick == null) {
            return
        }
        dirtySinceTick = null
        store.save(contextId, snapshot())
    }
}
