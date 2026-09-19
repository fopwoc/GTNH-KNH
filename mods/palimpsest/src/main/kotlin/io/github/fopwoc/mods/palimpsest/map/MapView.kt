package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.apache.logging.log4j.LogManager

/**
 * What a map screen asks for pixels. [frame] never blocks: it returns the pages that are ready and
 * schedules the rest on IO workers, bounded in parallelism; [onChanged] fires when a page becomes
 * ready or stale so the screen redraws. Pages that scroll out of view or belong to a time the user
 * has already scrubbed past are cancelled instead of finished.
 */
class MapView(
    private val store: MapPageStore,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    parallelism: Int = 4,
    private val maxReadyPages: Int = 512,
    private val onChanged: () -> Unit = {},
) : AutoCloseable {
    private val logger = LogManager.getLogger(MapView::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val permits = Semaphore(parallelism)
    private val lock = Any()
    private val ready =
        object : LinkedHashMap<MapPageKey, MapPageRaster?>(64, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<MapPageKey, MapPageRaster?>
            ): Boolean = size > maxReadyPages
        }
    private val building = HashMap<MapPageKey, Job>()
    private val stale = HashSet<MapPageKey>()
    /** Bumped on invalidation; a build that started before the bump leaves the page stale. */
    private val versions = HashMap<MapPageKey, Int>()
    private var time: MapTime = MapTime.Live
    private var wanted: Set<MapPageKey> = emptySet()
    private val invalidation: (Collection<MapPageKey>) -> Unit = ::invalidated

    init {
        require(parallelism > 0 && maxReadyPages > 0)
        store.addInvalidationListener(invalidation)
    }

    /**
     * Draw commands for every visible page that is ready; missing ones are being built and, while
     * they build, a cached page from a neighbouring level of detail stands in underneath so a zoom
     * across a level boundary never flashes a hole.
     */
    fun frame(camera: MapCamera, time: MapTime = MapTime.Live): GpuCanvasFrame {
        val pages = camera.visiblePages()
        val draws =
            synchronized(lock) {
                if (time != this.time) {
                    // Scrubbing: the previous moment stays on screen and each page swaps as its
                    // new build lands; builds for the old moment are dropped.
                    this.time = time
                    building.values.forEach(Job::cancel)
                    building.clear()
                    for (key in ready.keys) versions.merge(key, 1, Int::plus)
                    stale.addAll(ready.keys)
                }
                wanted = pages.toHashSet()
                val standIns = LinkedHashSet<MapPageKey>()
                val exact = ArrayList<GpuImageDraw>(pages.size)
                for (key in pages) {
                    if (!ready.containsKey(key) || key in stale) schedule(key, time)
                    if (ready.containsKey(key))
                        ready[key]?.let { exact += camera.draw(key, it.image) }
                    else standIns += standInsFor(key)
                }
                standIns.mapNotNull { key -> ready[key]?.let { camera.draw(key, it.image) } } +
                    exact
            }
        return GpuCanvasFrame(draws)
    }

    /** Coarser pages first so finer ones land on top; empty if nothing nearby is cached. */
    private fun standInsFor(key: MapPageKey): List<MapPageKey> {
        for (up in 1..STAND_IN_LEVELS) {
            val lod = key.lod + up
            if (lod > MapPageKey.MAX_LOD) break
            val ancestor = MapPageKey(key.x shr up, key.z shr up, lod)
            if (ready[ancestor] != null) return listOf(ancestor)
        }
        if (key.lod == 0) return emptyList()
        return buildList {
            for (dz in 0..1) for (dx in 0..1) {
                val child = MapPageKey(key.x * 2 + dx, key.z * 2 + dz, key.lod - 1)
                if (ready[child] != null) add(child)
            }
        }
    }

    /** Pages currently being built; zero means the last frame was complete. */
    fun pendingCount(): Int = synchronized(lock) { building.size }

    // A failed page must not take the worker down; it is logged and left unready.
    @Suppress("TooGenericExceptionCaught")
    private fun schedule(key: MapPageKey, time: MapTime) {
        if (key in building) return
        val version = versions[key] ?: 0
        building[key] = scope.launch {
            var raster: MapPageRaster? = null
            var built = false
            try {
                permits.withPermit {
                    val active = {
                        if (!isWanted(key, time)) throw CancellationException("superseded")
                    }
                    active()
                    raster =
                        when (time) {
                            MapTime.Live -> store.latest(key, active)
                            is MapTime.At -> store.historical(key, time.epoch, active)
                        }
                    built = true
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                logger.error("Building page {} failed", key, failure)
            } finally {
                synchronized(lock) {
                    if (building[key] === coroutineContext[Job]) building.remove(key)
                    if (built && this@MapView.time == time) {
                        ready[key] = raster
                        if ((versions[key] ?: 0) == version) stale -= key
                    }
                }
                if (built) onChanged()
            }
        }
    }

    private fun isWanted(key: MapPageKey, time: MapTime): Boolean =
        synchronized(lock) { this.time == time && key in wanted }

    /**
     * A stale page stays on screen until its replacement is ready; it is only marked so the next
     * frame schedules a rebuild, and a build already running is left to finish (it rebuilds once
     * more afterwards). Dropping or cancelling would flash a hole on every observation.
     */
    private fun invalidated(pages: Collection<MapPageKey>) {
        var changed = false
        synchronized(lock) {
            if (time != MapTime.Live) return
            for (page in pages) {
                versions.merge(page, 1, Int::plus)
                if (page in ready || page in building) stale += page
                if (page in wanted) changed = true
            }
        }
        if (changed) onChanged()
    }

    override fun close() {
        store.removeInvalidationListener(invalidation)
        scope.cancel()
        synchronized(lock) {
            ready.clear()
            stale.clear()
            versions.clear()
            building.clear()
        }
    }

    private companion object {
        /** How many coarser levels to search for a stand-in before falling back to children. */
        const val STAND_IN_LEVELS = 2
    }
}
