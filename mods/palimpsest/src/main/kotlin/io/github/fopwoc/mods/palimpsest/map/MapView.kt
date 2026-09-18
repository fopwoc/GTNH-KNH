package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
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
    private var time: MapTime = MapTime.Live
    private var wanted: Set<MapPageKey> = emptySet()
    private val invalidation: (Collection<MapPageKey>) -> Unit = ::invalidated

    init {
        require(parallelism > 0 && maxReadyPages > 0)
        store.addInvalidationListener(invalidation)
    }

    /** Draw commands for every visible page that is ready; missing ones are being built. */
    fun frame(camera: MapCamera, time: MapTime = MapTime.Live): GpuCanvasFrame {
        val pages = camera.visiblePages()
        val draws =
            synchronized(lock) {
                if (time != this.time) {
                    this.time = time
                    ready.clear()
                    building.values.forEach(Job::cancel)
                    building.clear()
                }
                wanted = pages.toHashSet()
                pages.mapNotNull { key ->
                    if (ready.containsKey(key)) ready[key]?.let { camera.draw(key, it.image) }
                    else {
                        schedule(key, time)
                        null
                    }
                }
            }
        return GpuCanvasFrame(draws)
    }

    /** Pages currently being built; zero means the last frame was complete. */
    fun pendingCount(): Int = synchronized(lock) { building.size }

    // A failed page must not take the worker down; it is logged and left unready.
    @Suppress("TooGenericExceptionCaught")
    private fun schedule(key: MapPageKey, time: MapTime) {
        if (key in building) return
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
                    if (built && this@MapView.time == time) ready[key] = raster
                }
                if (built) onChanged()
            }
        }
    }

    private fun isWanted(key: MapPageKey, time: MapTime): Boolean =
        synchronized(lock) { this.time == time && key in wanted }

    private fun invalidated(pages: Collection<MapPageKey>) {
        var changed = false
        synchronized(lock) {
            if (time != MapTime.Live) return
            for (page in pages) {
                if (ready.remove(page) != null || page in wanted) changed = true
                building.remove(page)?.cancel()
            }
        }
        if (changed) onChanged()
    }

    override fun close() {
        store.removeInvalidationListener(invalidation)
        scope.cancel()
        synchronized(lock) {
            ready.clear()
            building.clear()
        }
    }
}
