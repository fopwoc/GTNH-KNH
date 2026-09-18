package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.apache.logging.log4j.LogManager

/**
 * Immutable, content-addressed segments with a rebuildable tile/time index.
 *
 * Appends land in a local write-ahead log and are sealed into one segment per size or age
 * threshold, so the synced directory gains few files. Small segments are merged by [compact]. Reads
 * run concurrently under a shared lock; appends, seals, reloads and close take it exclusively.
 */
class TileHistoryStore(
    private val directory: Path,
    private val indexCacheEnabled: Boolean = true,
    residentIndexBytes: Long = DEFAULT_RESIDENT_INDEX_BYTES,
    private val sealBytes: Long = DEFAULT_SEAL_BYTES,
    sealAge: Duration = DEFAULT_SEAL_AGE,
    private val smallSegmentBytes: Long = DEFAULT_SMALL_SEGMENT_BYTES,
    private val compactFanIn: Int = DEFAULT_COMPACT_FAN_IN,
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
    private val index = TileIndex(residentIndexBytes)
    private val channels = ArrayList<FileChannel>()
    private val segmentFiles = ArrayList<TileIndexCache.Segment>()
    private val wal = WriteAheadLog(directory.resolve(WAL_NAME))
    private val walTiles = HashMap<TileKey, Int>()
    private val sealNanos = sealAge.toNanos()
    private var walOpenedNanos = 0L
    private val latestTiles =
        object : LinkedHashMap<TileKey, ByteArray>(256, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<TileKey, ByteArray>
            ): Boolean = size > 4096
        }
    private var bytes = 0L
    private var cacheDirty = false
    private var cachedIndex = false
    private var hashed = 0

    init {
        require(sealBytes > 0 && smallSegmentBytes > 0 && compactFanIn >= 2)
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
        get() = lock.read { wal.bytes }

    val walLayers: Int
        get() = lock.read { walTiles.values.sum() }

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
        get() = lock.read { segmentFiles.size }

    fun append(layers: List<TileLayer>): AppendResult = lock.write {
        if (layers.isEmpty()) return AppendResult(0, 0, 0, 0)
        validateEpochs(layers)
        val normalized =
            LayerNormalizer.normalize(layers) { key ->
                latestTiles[key]
                    ?: index.lastEpoch(key).takeIf { it >= 0 }?.let { read(key, it)?.colors }
            }
        val writing = normalized.layers
        if (writing.isEmpty()) return AppendResult(0, layers.size, 0, 0)
        val image = SegmentFormat.encode(writing)
        Files.createDirectories(directory)
        if (walTiles.isEmpty()) walOpenedNanos = System.nanoTime()
        val base = wal.append(image.bytes)
        for (record in image.records) {
            index.append(
                record.key,
                record.epoch,
                WAL_SEGMENT,
                base + record.offset,
                record.length,
                record.coverage,
                record.kind,
            )
            walTiles.merge(record.key, 1, Int::plus)
        }
        latestTiles.putAll(normalized.latest)
        cacheDirty = true
        logger.debug(
            "Logged {} layers ({} bytes) for {}; log holds {} bytes",
            writing.size,
            image.bytes.size,
            directory.fileName,
            wal.bytes,
        )
        if (wal.bytes >= sealBytes || System.nanoTime() - walOpenedNanos >= sealNanos) seal()
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

    /** Moves everything in the write-ahead log into one sealed, content-addressed segment. */
    fun seal(): Unit = lock.write {
        if (walTiles.isEmpty()) return
        val start = System.nanoTime()
        val layers = ArrayList<TileLayer>()
        for ((key, count) in walTiles) {
            val history = checkNotNull(index.history(key))
            for (entry in history.size - count until history.size) {
                check(history.segmentAt(entry) == WAL_SEGMENT)
                layers += readLayer(key, history, entry)
            }
        }
        val image = SegmentFormat.encode(layers)
        val segmentId = writeSegment(image)
        for ((key, count) in walTiles) index.truncateTail(key, count)
        for (record in image.records) {
            index.append(
                record.key,
                record.epoch,
                segmentId,
                record.offset,
                record.length,
                record.coverage,
                record.kind,
            )
        }
        walTiles.clear()
        wal.clear()
        cacheDirty = true
        logger.debug(
            "Sealed {} layers ({} bytes) of {} as segment {} in {} ms",
            layers.size,
            image.bytes.size,
            directory.fileName,
            segmentId,
            (System.nanoTime() - start) / 1_000_000,
        )
    }

    /**
     * Merges small segments once enough of them accumulated, rewriting each byte O(log n) times
     * over the life of a region. Returns true when a merge happened.
     */
    fun compact(): Boolean = lock.write {
        seal()
        val small = segmentFiles.indices.filter { segmentFiles[it].size < smallSegmentBytes }
        if (small.size < compactFanIn) return false
        val start = System.nanoTime()
        val merging = small.toHashSet()
        val layers = ArrayList<TileLayer>()
        for (key in index.keys()) {
            val history = checkNotNull(index.history(key))
            for (entry in 0 until history.size) {
                if (history.segmentAt(entry) in merging) layers += readLayer(key, history, entry)
            }
        }
        val image = SegmentFormat.encode(layers)
        val merged = small.sumOf { segmentFiles[it].size }
        val removed = small.map { segmentFiles[it].path }
        writeSegment(image)
        removed.forEach(Files::delete)
        reload()
        logger.info(
            "Compacted {} segments ({} bytes) of {} into {} bytes in {} ms",
            removed.size,
            merged,
            directory.fileName,
            image.bytes.size,
            (System.nanoTime() - start) / 1_000_000,
        )
        true
    }

    private fun writeSegment(image: SegmentFormat.Image): Int {
        Files.createDirectories(directory)
        val sealed = directory.resolve(image.sha256Name)
        if (Files.exists(sealed)) throw IOException("Segment already exists: $sealed")
        val temporary = Files.createTempFile(directory, ".palimpsest-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteBuffer.wrap(image.bytes)
                while (buffer.hasRemaining()) output.write(buffer)
                output.force(true)
            }
            Files.move(temporary, sealed, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
        val segmentId = channels.size
        channels += FileChannel.open(sealed, StandardOpenOption.READ)
        segmentFiles += TileIndexCache.Segment(sealed, image.bytes.size.toLong())
        bytes += image.bytes.size
        refreshRanks()
        return segmentId
    }

    fun read(key: TileKey, epoch: Long): TileRead? = lock.read {
        val history = index.history(key) ?: return null
        val firstAfter = history.firstAfter(epoch)
        if (firstAfter == 0) return null
        val result = ByteArray(TileLayer.PIXELS)
        val missing = LongArray(TileLayer.MASK_WORDS) { -1L }
        var visited = 0
        var decoded = 0
        var skipped = 0
        var entryIndex = firstAfter - 1
        while (entryIndex >= 0) {
            val group = entryIndex / PackedTileHistory.GROUP_SIZE
            if (!history.groupCanFill(group, missing)) {
                skipped += entryIndex - group * PackedTileHistory.GROUP_SIZE + 1
                entryIndex = group * PackedTileHistory.GROUP_SIZE - 1
                continue
            }
            val groupStart = group * PackedTileHistory.GROUP_SIZE
            while (entryIndex >= groupStart) {
                visited++
                if (history.layerCanFill(entryIndex, missing)) {
                    val layer = readLayer(key, history, entryIndex)
                    decoded++
                    var colorIndex = 0
                    for (position in 0 until TileLayer.PIXELS) {
                        val bit = 1L shl (position and 63)
                        val word = position ushr 6
                        if (layer.coverage[word] and bit == 0L) continue
                        if (missing[word] and bit != 0L) result[position] = layer.colors[colorIndex]
                        colorIndex++
                    }
                    for (word in missing.indices) missing[word] =
                        missing[word] and layer.coverage[word].inv()
                    if (missing.all { it == 0L }) return TileRead(result, visited, decoded, skipped)
                }
                entryIndex--
            }
        }
        throw CorruptHistoryException("Tile $key has no complete initial layer")
    }

    /** Resolves selected indexed colors using coverage masks and positional record reads. */
    fun readSamples(key: TileKey, epoch: Long, positions: IntArray): SampleRead? {
        require(positions.isNotEmpty() && positions.size <= TileLayer.PIXELS)
        require(positions.all { it in 0 until TileLayer.PIXELS })
        require((1 until positions.size).all { positions[it - 1] < positions[it] })
        return lock.read {
            val history = index.history(key) ?: return null
            var entryIndex = history.firstAfter(epoch) - 1
            if (entryIndex < 0) return null
            val missing = LongArray(TileLayer.MASK_WORDS)
            for (position in positions) {
                missing[position ushr 6] = missing[position ushr 6] or (1L shl (position and 63))
            }
            val colors = ByteArray(positions.size)
            var bytesRead = 0
            var visited = 0
            var decoded = 0
            while (entryIndex >= 0) {
                val group = entryIndex / PackedTileHistory.GROUP_SIZE
                if (!history.groupCanFill(group, missing)) {
                    entryIndex = group * PackedTileHistory.GROUP_SIZE - 1
                    continue
                }
                val groupStart = group * PackedTileHistory.GROUP_SIZE
                while (entryIndex >= groupStart) {
                    visited++
                    if (history.layerCanFill(entryIndex, missing)) {
                        bytesRead +=
                            readLayerSamples(history, entryIndex, positions, missing, colors)
                        decoded++
                        if (missing.all { it == 0L })
                            return SampleRead(colors, bytesRead, visited, decoded)
                    }
                    entryIndex--
                }
            }
            throw CorruptHistoryException("Tile $key has no complete initial layer")
        }
    }

    fun readPixel(key: TileKey, epoch: Long, position: Int): Int? =
        readSamples(key, epoch, intArrayOf(position))?.colors?.get(0)?.toInt()?.and(255)

    @Suppress("CyclomaticComplexMethod", "ThrowsCount")
    private fun readLayerSamples(
        history: PackedTileHistory,
        index: Int,
        positions: IntArray,
        missing: LongArray,
        output: ByteArray,
    ): Int {
        val length = history.lengthAt(index)
        val channel = channelOf(history.segmentAt(index))
        val kind = history.kindAt(index)
        if (kind == AdaptiveLayerCodec.FULL_EXCEPTIONS) {
            val body = ByteBuffer.allocate(length)
            SegmentFormat.readFully(channel, history.offsetAt(index), body)
            if (body.get(0).toInt() != AdaptiveLayerCodec.FULL_EXCEPTIONS) {
                throw CorruptHistoryException("Invalid exception record")
            }
            var payload = 1
            while (payload < length) {
                if (body.get(payload++).toInt() and 128 == 0) break
            }
            if (payload + 2 > length) throw CorruptHistoryException("Truncated exception record")
            val base = body.get(payload)
            val count = body.get(payload + 1).toInt() and 255
            if (count !in 1..16 || payload + 2 + count * 2 != length) {
                throw CorruptHistoryException("Invalid exception record length")
            }
            for ((resultIndex, position) in positions.withIndex()) {
                if (missing[position ushr 6] and (1L shl (position and 63)) != 0L) {
                    output[resultIndex] = base
                }
            }
            var previousPosition = -1
            repeat(count) { exception ->
                val position = body.get(payload + 2 + exception * 2).toInt() and 255
                if (position <= previousPosition) {
                    throw CorruptHistoryException("Unsorted exception positions")
                }
                val resultIndex = positions.binarySearch(position)
                if (
                    resultIndex >= 0 &&
                        missing[position ushr 6] and (1L shl (position and 63)) != 0L
                ) {
                    output[resultIndex] = body.get(payload + 3 + exception * 2)
                }
                previousPosition = position
            }
            for (position in positions) {
                missing[position ushr 6] =
                    missing[position ushr 6] and (1L shl (position and 63)).inv()
            }
            return length
        }
        val covered =
            (0 until TileLayer.MASK_WORDS).sumOf {
                java.lang.Long.bitCount(history.maskAt(index, it))
            }
        // The body length and coverage determine where colors start for every encoding.
        val colorStart =
            when (kind) {
                AdaptiveLayerCodec.SPARSE -> length - 2 * covered
                AdaptiveLayerCodec.FULL_SOLID,
                AdaptiveLayerCodec.MASKED_SOLID -> length - 1
                else -> length - covered
            }
        val selectedOffsets = IntArray(positions.size) { -1 }
        var firstOffset = length
        var lastOffset = -1
        for ((resultIndex, position) in positions.withIndex()) {
            val word = position ushr 6
            val bit = 1L shl (position and 63)
            if (missing[word] and bit == 0L || history.maskAt(index, word) and bit == 0L) continue
            var rank = 0
            for (earlier in 0 until word) rank +=
                java.lang.Long.bitCount(history.maskAt(index, earlier))
            rank += java.lang.Long.bitCount(history.maskAt(index, word) and (bit - 1))
            val offset =
                when (kind) {
                    AdaptiveLayerCodec.SPARSE -> colorStart + rank * 2 + 1
                    AdaptiveLayerCodec.MASKED -> colorStart + rank
                    AdaptiveLayerCodec.FULL_SOLID,
                    AdaptiveLayerCodec.MASKED_SOLID -> colorStart
                    else -> colorStart + position
                }
            if (offset !in 0 until length) {
                throw CorruptHistoryException("Invalid sample color offset")
            }
            selectedOffsets[resultIndex] = offset
            firstOffset = minOf(firstOffset, offset)
            lastOffset = maxOf(lastOffset, offset)
        }
        if (lastOffset < 0) return 0
        val payloadBytes = ByteBuffer.allocate(lastOffset - firstOffset + 1)
        SegmentFormat.readFully(channel, history.offsetAt(index) + firstOffset, payloadBytes)
        for ((resultIndex, offset) in selectedOffsets.withIndex()) {
            if (offset < 0) continue
            val position = positions[resultIndex]
            output[resultIndex] = payloadBytes.get(offset - firstOffset)
            missing[position ushr 6] = missing[position ushr 6] and (1L shl (position and 63)).inv()
        }
        return payloadBytes.capacity()
    }

    fun hasChanges(key: TileKey, firstEpoch: Long, secondEpoch: Long): Boolean = lock.read {
        if (firstEpoch == secondEpoch) return false
        val history = index.history(key) ?: return false
        history.firstAfter(minOf(firstEpoch, secondEpoch)) !=
            history.firstAfter(maxOf(firstEpoch, secondEpoch))
    }

    /** Seals pending layers and persists the index sidecar; safe to call from a background tick. */
    fun flush(): Unit = lock.write { persistIndexCache() }

    @Suppress("TooGenericExceptionCaught")
    fun reload(): Unit = lock.write {
        val start = System.nanoTime()
        persistIndexCache()
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
                    logger.info("Dropped {} duplicate layers while indexing {}", dropped, directory)
                }
                cacheDirty = true
                persistIndexCache()
            }
            replayLog()
        } catch (failure: Throwable) {
            resetIndex()
            throw failure
        }
        logger.debug(
            "Opened {}: {} segments, {} tiles, {} layers ({} pending) in {} ms (index cache {}, {} hashed)",
            directory.fileName,
            channels.size,
            index.tileCount,
            index.recordCount,
            walTiles.values.sum(),
            (System.nanoTime() - start) / 1_000_000,
            if (cachedIndex) "hit" else "miss",
            hashed,
        )
    }

    private fun replayLog() {
        val frames = wal.replay()
        if (frames.isEmpty()) return
        for (frame in frames) {
            SegmentFormat.parse(wal.reader, frame.offset, frame.length.toLong()) {
                key,
                epoch,
                offset,
                length,
                coverage,
                kind ->
                index.append(key, epoch, WAL_SEGMENT, offset, length, coverage, kind)
                walTiles.merge(key, 1, Int::plus)
            }
        }
        walOpenedNanos = System.nanoTime()
        cacheDirty = true
    }

    private fun resetIndex() {
        channels.forEach(FileChannel::close)
        channels.clear()
        segmentFiles.clear()
        index.clear()
        latestTiles.clear()
        walTiles.clear()
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
                for (file in loaded.segments) openSegment(file.path, file.size)
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
            index.saved(TileIndexCache.open(cache, segmentFiles))
            logger.debug(
                "Saved index cache for {} ({} layers) in {} ms",
                directory.fileName,
                index.recordCount,
                (System.nanoTime() - start) / 1_000_000,
            )
        } catch (failure: Exception) {
            logger.warn("Could not save disposable index cache {}: {}", cache, failure.toString())
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
            if (segmentId == WAL_SEGMENT) Int.MAX_VALUE else ranks[segmentId]
        }
    }

    private fun channelOf(segmentId: Int): FileChannel =
        if (segmentId == WAL_SEGMENT) wal.reader else channels[segmentId]

    private fun readLayer(key: TileKey, history: PackedTileHistory, index: Int): TileLayer {
        val channel = channelOf(history.segmentAt(index))
        val body = ByteBuffer.allocate(history.lengthAt(index))
        SegmentFormat.readFully(channel, history.offsetAt(index), body)
        return AdaptiveLayerCodec.decode(body.array(), key, history.epochAt(index))
    }

    override fun close(): Unit = lock.write {
        persistIndexCache()
        channels.forEach(FileChannel::close)
        channels.clear()
        latestTiles.clear()
        index.clear()
        wal.close()
    }

    companion object {
        const val WAL_NAME = ".pending.wal"
        const val MIN_CACHED_RECORDS = 256
        const val DEFAULT_RESIDENT_INDEX_BYTES = 16L shl 20
        const val DEFAULT_SEAL_BYTES = 1L shl 20
        val DEFAULT_SEAL_AGE: Duration = Duration.ofMinutes(5)
        const val DEFAULT_SMALL_SEGMENT_BYTES = 4L shl 20
        const val DEFAULT_COMPACT_FAN_IN = 8
        private const val WAL_SEGMENT = PackedTileHistory.MAX_SEGMENT
    }
}
