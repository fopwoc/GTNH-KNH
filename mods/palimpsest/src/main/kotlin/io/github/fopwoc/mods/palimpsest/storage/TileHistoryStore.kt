package io.github.fopwoc.mods.palimpsest.storage

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write
import org.apache.logging.log4j.LogManager

/**
 * Immutable, content-addressed segments with a rebuildable tile/time index.
 *
 * Appends land in one of two local write-ahead logs. Sealing freezes the active log, switches
 * appends to the other, builds the segment without holding the store lock and swaps index entries
 * under it; compaction merges small segments the same way. Tiles whose history since the last full
 * layer grew past [checkpointInterval] get a full snapshot when sealed, bounding every read.
 */
class TileHistoryStore(
    private val directory: Path,
    private val indexCacheEnabled: Boolean = true,
    residentIndexBytes: Long = DEFAULT_RESIDENT_INDEX_BYTES,
    private val sealBytes: Long = DEFAULT_SEAL_BYTES,
    sealAge: Duration = DEFAULT_SEAL_AGE,
    private val smallSegmentBytes: Long = DEFAULT_SMALL_SEGMENT_BYTES,
    private val compactFanIn: Int = DEFAULT_COMPACT_FAN_IN,
    private val checkpointInterval: Int = DEFAULT_CHECKPOINT_INTERVAL,
) : AutoCloseable {
    private val logger = LogManager.getLogger(TileHistoryStore::class.java)

    data class TileRead(
        val colors: ByteArray,
        val layersVisited: Int,
        val layersDecoded: Int,
        val layersSkipped: Int,
    )

    data class SampleRead(
        val colors: ByteArray,
        val bytesRead: Int,
        val layersVisited: Int,
        val layersDecoded: Int,
    )

    data class AppendResult(
        val layersWritten: Int,
        val layersDiscarded: Int,
        val coveredCells: Int,
        val bytesAdded: Long,
    )

    private val lock = ReentrantReadWriteLock()
    /** Serializes seal and compaction, which do most of their work outside [lock]. */
    private val maintenance = ReentrantLock()
    /** Serializes appenders and log switches; log I/O happens under it, not under [lock]. */
    private val appendLock = ReentrantLock()
    private val index = TileIndex(residentIndexBytes)
    private val reader = LayerReader(::channelOf)
    private val channels = ArrayList<FileChannel?>()
    private val segmentFiles = ArrayList<TileIndexCache.Segment>()
    private val logs =
        arrayOf(WriteAheadLog(directory.resolve(LOG_A)), WriteAheadLog(directory.resolve(LOG_B)))
    private val logCounts = arrayOf(HashMap<TileKey, Int>(), HashMap<TileKey, Int>())
    private var active = 0
    private var sequence = 0L
    private val sealNanos = sealAge.toNanos()
    private var logOpenedNanos = 0L
    private val latestTiles =
        object : LinkedHashMap<TileKey, ByteArray>(64, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<TileKey, ByteArray>
            ): Boolean = size > LATEST_TILES
        }
    private var bytes = 0L
    private var cacheDirty = false
    private var cachedIndex = false
    private var hashed = 0
    @Volatile private var sealDue = false

    init {
        require(
            sealBytes > 0 && smallSegmentBytes > 0 && compactFanIn >= 2 && checkpointInterval > 0
        )
        reload()
    }

    val tileCount: Int
        get() = lock.read { index.tileCount }

    val layerCount: Long
        get() = lock.read { index.recordCount }

    /** Bytes in sealed segments; pending log bytes are [walBytes]. */
    val byteCount: Long
        get() = lock.read { bytes }

    val walBytes: Long
        get() = lock.read { logs.sumOf { it.bytes } }

    val walLayers: Int
        get() = lock.read { logCounts.sumOf { counts -> counts.values.sum() } }

    /** Primitive arrays of the tiles currently resident; cold tiles live in the sidecar. */
    val indexArrayBytes: Long
        get() = lock.read { index.residentBytes }

    val residentTiles: Int
        get() = lock.read { index.residentTiles }

    val latestEpoch: Long
        get() = lock.read { index.latestEpoch }

    val loadedFromIndexCache: Boolean
        get() = lock.read { cachedIndex }

    /** Segments hashed by the last open; trusted segments verified earlier in this process. */
    val segmentsHashed: Int
        get() = lock.read { hashed }

    val segmentCount: Int
        get() = lock.read { segmentFiles.count { !it.isTombstone } }

    /** True once the active log passed its size or age threshold; [seal] clears it. */
    val isSealDue: Boolean
        get() = sealDue

    /**
     * Appenders are serialized by [appendLock]; the store lock is held only to normalize against
     * the current tiles and, after the log write, to publish the new index entries, so readers
     * never wait on log I/O.
     */
    fun append(layers: List<TileLayer>): AppendResult = appendLock.withLock {
        if (layers.isEmpty()) return AppendResult(0, 0, 0, 0)
        val normalized = lock.read {
            validateEpochs(layers)
            LayerNormalizer.normalize(layers) { key ->
                latestTiles[key]
                    ?: index.lastEpoch(key).takeIf { it >= 0 }?.let { read(key, it)?.colors }
            }
        }
        val writing = normalized.layers
        if (writing.isEmpty()) return AppendResult(0, layers.size, 0, 0)
        val image = SegmentFormat.encode(writing)
        Files.createDirectories(directory)
        // The active log only flips under appendLock, so this choice stays valid below.
        val log = active
        val base = logs[log].append(++sequence, image.bytes)
        lock.write {
            val counts = logCounts[log]
            if (counts.isEmpty()) logOpenedNanos = System.nanoTime()
            val logSegment = logSegment(log)
            for (record in image.records) {
                index.append(
                    record.key,
                    record.epoch,
                    logSegment,
                    base + record.offset,
                    record.length,
                    record.coverage,
                    record.kind,
                )
                counts.merge(record.key, 1, Int::plus)
            }
            latestTiles.putAll(normalized.latest)
            cacheDirty = true
            if (logs[log].bytes >= sealBytes || System.nanoTime() - logOpenedNanos >= sealNanos) {
                sealDue = true
            }
        }
        AppendResult(
            writing.size,
            layers.size - writing.size,
            writing.sumOf { it.colors.size },
            image.bytes.size.toLong(),
        )
    }

    /** Rejects the whole batch before anything is written when tile epochs do not increase. */
    fun validateEpochs(layers: List<TileLayer>): Unit = lock.read {
        val lastInBatch = HashMap<TileKey, Long>()
        for (layer in layers) {
            val previous = lastInBatch[layer.key] ?: index.lastEpoch(layer.key)
            require(layer.epoch > previous) { "Tile epochs must increase for ${layer.key}" }
            lastInBatch[layer.key] = layer.epoch
        }
    }

    /** Seals the active log into one segment; appends continue into the other log meanwhile. */
    fun seal(): Unit = maintenance.withLock { sealFrozen(freezeActiveLog() ?: return) }

    /** What a maintenance tick calls: seals when the active log passed its threshold. */
    fun sealIfDue(): Boolean {
        if (!sealDue) return false
        seal()
        return true
    }

    private class Frozen(val log: Int, val counts: Map<TileKey, Int>)

    private fun freezeActiveLog(): Frozen? = appendLock.withLock {
        lock.write {
            sealDue = false
            val counts = logCounts[active]
            if (counts.isEmpty()) return null
            val frozen = Frozen(active, counts)
            logCounts[active] = HashMap()
            active = 1 - active
            check(logCounts[active].isEmpty() && logs[active].bytes == 0L) { "Both logs pending" }
            frozen
        }
    }

    private fun sealFrozen(frozen: Frozen) {
        val start = System.nanoTime()
        val logSegment = logSegment(frozen.log)
        val logBytes = logs[frozen.log].readAll()
        val runs = HashMap<TileKey, Int>()
        val layers = ArrayList<TileLayer>()
        var checkpoints = 0
        for ((key, count) in frozen.counts) {
            lock.read {
                val history = checkNotNull(index.history(key))
                val end = history.size - (logCounts[active][key] ?: 0)
                val from = end - count
                runs[key] = from
                for (entry in from until end) {
                    check(history.segmentAt(entry) == logSegment)
                    val offset = history.offsetAt(entry).toInt()
                    val body = logBytes.copyOfRange(offset, offset + history.lengthAt(entry))
                    layers += AdaptiveLayerCodec.decode(body, key, history.epochAt(entry))
                }
                if (history.recordsSinceFull() >= checkpointInterval) {
                    val epoch = history.epochAt(end - 1)
                    val colors = checkNotNull(read(key, epoch)).colors
                    layers[layers.lastIndex] = TileLayer.snapshot(key, epoch, colors)
                    checkpoints++
                }
            }
        }
        val collected = System.nanoTime()
        val image = SegmentFormat.encode(layers)
        val sealed = SegmentFormat.writeSealed(directory, image)
        val written = System.nanoTime()
        lock.write {
            val segmentId = registerSegment(sealed, image.bytes.size.toLong())
            val byTile = image.records.groupBy(SegmentFormat.Record::key)
            for ((key, from) in runs) {
                val count = frozen.counts.getValue(key)
                val replacement =
                    byTile.getValue(key).map {
                        PackedTileHistory.Entry(
                            it.epoch,
                            segmentId,
                            it.offset,
                            it.length,
                            it.coverage,
                            it.kind,
                        )
                    }
                index.replaceRange(key, from, from + count, replacement)
            }
            logs[frozen.log].clear()
            cacheDirty = true
        }
        logger.debug(
            "Sealed {} layers ({} bytes, {} checkpoints) of {} in {} ms: collect {} ms, encode+write {} ms, publish {} ms",
            layers.size,
            image.bytes.size,
            checkpoints,
            directory.fileName,
            (System.nanoTime() - start) / 1_000_000,
            (collected - start) / 1_000_000,
            (written - collected) / 1_000_000,
            (System.nanoTime() - written) / 1_000_000,
        )
    }

    /**
     * Merges small sealed segments once enough of them accumulated, rewriting each byte O(log n)
     * times over the life of a region; pending log layers are left to [seal]. Only the tiles
     * present in the merged segments are re-indexed; everything else, including their sidecar
     * blocks, stays valid. Returns true when a merge ran.
     */
    fun compact(): Boolean = maintenance.withLock {
        val start = System.nanoTime()
        val small = lock.read {
            segmentFiles.indices.filter {
                val file = segmentFiles[it]
                !file.isTombstone && file.size < smallSegmentBytes
            }
        }
        if (small.size < compactFanIn) return false
        val layers = ArrayList<TileLayer>()
        var merged = 0L
        for (segmentId in small) {
            val (channel, size) =
                lock.read { checkNotNull(channels[segmentId]) to segmentFiles[segmentId].size }
            merged += size
            SegmentFormat.parse(channel, 0, size) { key, epoch, offset, length, _, _ ->
                val body = ByteBuffer.allocate(length)
                SegmentFormat.readFully(channel, offset, body)
                layers += AdaptiveLayerCodec.decode(body.array(), key, epoch)
            }
        }
        val image = SegmentFormat.encode(layers)
        val sealed = SegmentFormat.writeSealed(directory, image)
        val mergedSet = small.toHashSet()
        val sealedSize = image.bytes.size.toLong()
        val segmentId = lock.write { registerSegment(sealed, sealedSize) }
        // Rebuild affected tiles under the read lock; appends that land meanwhile are replayed.
        val rebuilt = HashMap<TileKey, Pair<PackedTileHistory, Int>>()
        for ((key, records) in image.records.groupBy(SegmentFormat.Record::key)) {
            lock.read {
                val history = checkNotNull(index.history(key))
                val replacement = PackedTileHistory.forReload()
                for (entry in 0 until history.size) {
                    if (history.segmentAt(entry) in mergedSet) continue
                    val e = history.entryAt(entry)
                    replacement.add(e.epoch, e.segment, e.offset, e.length, e.mask, e.kind)
                }
                for (record in records) {
                    replacement.add(
                        record.epoch,
                        segmentId,
                        record.offset,
                        record.length,
                        record.coverage,
                        record.kind,
                    )
                }
                replacement.finishReload(index.segmentRank)
                rebuilt[key] = replacement to history.size
            }
        }
        lock.write {
            for ((key, built) in rebuilt) index.replaceTileBuilt(key, built.first, built.second)
            for (id in small) {
                channels[id]?.close()
                channels[id] = null
                val file = segmentFiles[id]
                segmentFiles[id] = TileIndexCache.Segment(file.path, -1)
                bytes -= file.size
                Files.deleteIfExists(file.path)
            }
            refreshRanks()
            cacheDirty = true
        }
        logger.info(
            "Compacted {} segments ({} bytes) of {} into {} bytes in {} ms",
            small.size,
            merged,
            directory.fileName,
            image.bytes.size,
            (System.nanoTime() - start) / 1_000_000,
        )
        true
    }

    private fun registerSegment(sealed: Path, size: Long): Int {
        val segmentId = channels.size
        require(segmentId <= PackedTileHistory.MAX_SEGMENT) { "Too many segments in $directory" }
        channels += FileChannel.open(sealed, StandardOpenOption.READ)
        segmentFiles += TileIndexCache.Segment(sealed, size)
        bytes += size
        refreshRanks()
        return segmentId
    }

    fun read(key: TileKey, epoch: Long): TileRead? =
        withHistory(key) { history -> reader.read(key, history ?: return null, epoch) }

    /** Resolves selected indexed colors using coverage masks and positional record reads. */
    fun readSamples(key: TileKey, epoch: Long, positions: IntArray): SampleRead? =
        withHistory(key) { history ->
            reader.readSamples(key, history ?: return null, epoch, positions)
        }

    fun readPixel(key: TileKey, epoch: Long, position: Int): Int? =
        readSamples(key, epoch, intArrayOf(position))?.colors?.get(0)?.toInt()?.and(255)

    fun hasChanges(key: TileKey, firstEpoch: Long, secondEpoch: Long): Boolean {
        if (firstEpoch == secondEpoch) return false
        return withHistory(key) { history ->
            history ?: return false
            history.firstAfter(minOf(firstEpoch, secondEpoch)) !=
                history.firstAfter(maxOf(firstEpoch, secondEpoch))
        }
    }

    /**
     * Runs [action] on a tile's history under the read lock. A damaged sidecar block is the one
     * failure that is not the data's fault: the sidecar is dropped, the index rebuilt from the
     * segments, and the action retried once.
     */
    private inline fun <T> withHistory(key: TileKey, action: (PackedTileHistory?) -> T): T {
        try {
            return lock.read { action(index.history(key)) }
        } catch (failure: IndexCacheException) {
            recoverIndex(failure)
        }
        return lock.read { action(index.history(key)) }
    }

    private fun recoverIndex(failure: IndexCacheException) = maintenance.withLock {
        lock.write {
            logger.warn(
                "Rebuilding index of {} after sidecar damage: {}",
                directory,
                failure.toString(),
            )
            seal()
            cacheDirty = false
            Files.deleteIfExists(TileIndexCache.path(directory))
            reload()
        }
    }

    /** Seals pending layers and persists the index sidecar; safe to call from a background tick. */
    fun flush(): Unit = maintenance.withLock { persistIndexCache() }

    @Suppress("TooGenericExceptionCaught")
    fun reload(): Unit = maintenance.withLock {
        val start = System.nanoTime()
        persistIndexCache()
        lock.write {
            resetIndex()
            if (!Files.isDirectory(directory)) return
            val files =
                Files.list(directory).use { paths ->
                    paths
                        .filter { it.fileName.toString().endsWith(SegmentFormat.EXTENSION) }
                        .sorted()
                        .map { TileIndexCache.Segment(it, Files.size(it)) }
                        .toList()
                }
            try {
                if (!(indexCacheEnabled && files.isNotEmpty() && loadCachedIndex(files))) {
                    files.forEach(::indexSegment)
                    refreshRanks()
                    val dropped = index.finishReload()
                    if (dropped > 0) {
                        logger.info(
                            "Dropped {} duplicate layers while indexing {}",
                            dropped,
                            directory,
                        )
                    }
                    cacheDirty = true
                    persistIndexCache()
                }
                replayLogs()
            } catch (failure: Throwable) {
                resetIndex()
                throw failure
            }
            logger.debug(
                "Opened {}: {} segments, {} tiles, {} layers ({} pending) in {} ms (index cache {}, {} hashed)",
                directory.fileName,
                segmentFiles.size,
                index.tileCount,
                index.recordCount,
                walLayers,
                (System.nanoTime() - start) / 1_000_000,
                if (cachedIndex) "hit" else "miss",
                hashed,
            )
        }
    }

    private fun replayLogs() {
        val frames =
            logs.indices
                .flatMap { log -> logs[log].replay().map { frame -> log to frame } }
                .sortedBy { it.second.sequence }
        if (frames.isEmpty()) return
        for ((log, frame) in frames) {
            val logSegment = logSegment(log)
            SegmentFormat.parse(logs[log].reader, frame.offset, frame.length.toLong()) {
                key,
                epoch,
                offset,
                length,
                coverage,
                kind ->
                index.append(key, epoch, logSegment, offset, length, coverage, kind)
                logCounts[log].merge(key, 1, Int::plus)
            }
        }
        sequence = frames.last().second.sequence
        active = frames.last().first
        // A crash between freezing and clearing leaves both logs pending: seal the older first.
        if (logCounts[1 - active].isNotEmpty()) {
            val older = Frozen(1 - active, logCounts[1 - active])
            logCounts[1 - active] = HashMap()
            sealFrozen(older)
        }
        logOpenedNanos = System.nanoTime()
        cacheDirty = true
    }

    private fun resetIndex() {
        channels.forEach { it?.close() }
        channels.clear()
        segmentFiles.clear()
        index.clear()
        latestTiles.clear()
        logCounts.forEach { it.clear() }
        bytes = 0
        cachedIndex = false
        cacheDirty = false
        hashed = 0
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadCachedIndex(files: List<TileIndexCache.Segment>): Boolean {
        val cache = TileIndexCache.path(directory)
        try {
            val loaded = TileIndexCache.open(cache, files) ?: return false
            try {
                // The sidecar's segment order is authoritative so its segment IDs stay valid.
                for (file in loaded.segments) {
                    if (file.isTombstone) {
                        channels += null
                        segmentFiles += file
                    } else {
                        openSegment(file.path, file.size)
                    }
                }
                refreshRanks()
                index.adoptCold(loaded)
            } catch (failure: Throwable) {
                loaded.close()
                throw failure
            }
            cachedIndex = true
            return true
        } catch (failure: Exception) {
            logger.warn("Discarding index cache {}: {}", cache, failure.toString())
            resetIndex()
            return false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun persistIndexCache() {
        seal()
        lock.write {
            if (!cacheDirty) return
            cacheDirty = false
            if (!indexCacheEnabled) return
            val cache = TileIndexCache.path(directory)
            try {
                // Parsing a handful of records is cheaper than opening and checksumming a sidecar.
                if (index.recordCount < MIN_CACHED_RECORDS) {
                    index.saved(null)
                    Files.deleteIfExists(cache)
                    return
                }
                val start = System.nanoTime()
                TileIndexCache.save(cache, segmentFiles, index.blocks())
                index.saved(TileIndexCache.open(cache, segmentFiles.filterNot { it.isTombstone }))
                logger.debug(
                    "Saved index cache for {} ({} layers) in {} ms",
                    directory.fileName,
                    index.recordCount,
                    (System.nanoTime() - start) / 1_000_000,
                )
            } catch (failure: Exception) {
                logger.warn(
                    "Could not save disposable index cache {}: {}",
                    cache,
                    failure.toString(),
                )
            }
        }
    }

    private fun indexSegment(file: TileIndexCache.Segment) {
        val segmentId = channels.size
        openSegment(file.path, file.size) { channel ->
            SegmentFormat.parse(channel, 0, file.size) { key, epoch, offset, length, coverage, kind
                ->
                index.addParsed(key, epoch, segmentId, offset, length, coverage, kind)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun openSegment(file: Path, size: Long, index: (FileChannel) -> Unit = {}) {
        val channel = FileChannel.open(file, StandardOpenOption.READ)
        try {
            if (SegmentVerifier.verify(channel, file, size, SegmentFormat.MAGIC)) hashed++
            index(channel)
            bytes += size
            channels += channel
            segmentFiles += TileIndexCache.Segment(file, size)
        } catch (failure: Throwable) {
            channel.close()
            throw failure
        }
    }

    /** Duplicate epochs resolve toward the segment whose name sorts first on every machine. */
    private fun refreshRanks() {
        val byName = segmentFiles.indices.sortedBy { segmentFiles[it].path.fileName.toString() }
        val ranks = IntArray(segmentFiles.size)
        byName.forEachIndexed { rank, segmentId -> ranks[segmentId] = rank }
        index.segmentRank = { segmentId ->
            if (segmentId > PackedTileHistory.MAX_SEGMENT) Int.MAX_VALUE else ranks[segmentId]
        }
    }

    private fun channelOf(segmentId: Int): FileChannel =
        when (segmentId) {
            PackedTileHistory.LOG_SEGMENT_A -> logs[0].reader
            PackedTileHistory.LOG_SEGMENT_B -> logs[1].reader
            else -> checkNotNull(channels[segmentId]) { "Segment $segmentId was compacted away" }
        }

    override fun close(): Unit = maintenance.withLock {
        persistIndexCache()
        lock.write {
            channels.forEach { it?.close() }
            channels.clear()
            latestTiles.clear()
            index.clear()
            logs.forEach(WriteAheadLog::close)
        }
    }

    companion object {
        const val LOG_A = ".pending-a.wal"
        const val LOG_B = ".pending-b.wal"
        const val MIN_CACHED_RECORDS = 256
        const val LATEST_TILES = 256
        const val DEFAULT_RESIDENT_INDEX_BYTES = 16L shl 20
        const val DEFAULT_SEAL_BYTES = 1L shl 20
        val DEFAULT_SEAL_AGE: Duration = Duration.ofMinutes(5)
        const val DEFAULT_SMALL_SEGMENT_BYTES = 4L shl 20
        const val DEFAULT_COMPACT_FAN_IN = 8
        const val DEFAULT_CHECKPOINT_INTERVAL = 64
    }
}

private fun logSegment(log: Int): Int =
    if (log == 0) PackedTileHistory.LOG_SEGMENT_A else PackedTileHistory.LOG_SEGMENT_B
