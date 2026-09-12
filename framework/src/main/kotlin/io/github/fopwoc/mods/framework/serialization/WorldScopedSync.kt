package io.github.fopwoc.mods.framework.serialization

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientWorldContext

/**
 * Drives a [WorldScopedJsonStore] from the client tick: loads when the world/server changes
 * (`onLoaded(null)` when there is none), saves [debounceTicks] after [markDirty], and flushes on a
 * context change. Call [tick] from a `ClientTickEvent` and [flush] on disconnect.
 */
@SideOnly(Side.CLIENT)
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
    val contextId = ClientWorldContext.currentId()
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
