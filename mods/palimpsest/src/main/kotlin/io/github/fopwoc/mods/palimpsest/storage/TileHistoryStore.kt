package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.apache.logging.log4j.LogManager

/**
 * Immutable, content-addressed segments with a rebuildable in-memory tile/time index.
 *
 * Reads run concurrently under a shared lock; appends, reloads and close take it exclusively.
 */
class TileHistoryStore(private val directory: Path, private val indexCacheEnabled: Boolean = true) :
    AutoCloseable {
    private val logger = LogManager.getLogger(TileHistoryStore::class.java)

    private data class WrittenRecord(
        val key: TileKey,
        val offset: Long,
        val length: Int,
        val kind: Int,
        val epoch: Long,
        val coverage: LongArray,
    )

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
    private val index = HashMap<TileKey, PackedTileHistory>()
    private val channels = ArrayList<FileChannel>()
    private val segmentFiles = ArrayList<TileIndexCache.Segment>()
    private val latestTiles =
        object : LinkedHashMap<TileKey, ByteArray>(256, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<TileKey, ByteArray>
            ): Boolean = size > 4096
        }
    private var bytes = 0L
    private var records = 0
    private var latest = 0L
    private var cacheDirty = false
    private var cachedIndex = false
    private var hashed = 0

    init {
        reload()
    }

    val tileCount: Int
        get() = lock.read { index.size }

    val layerCount: Int
        get() = lock.read { records }

    val byteCount: Long
        get() = lock.read { bytes }

    val indexArrayBytes: Long
        get() = lock.read { index.values.sumOf(PackedTileHistory::arrayBytes) }

    val latestEpoch: Long
        get() = lock.read { latest }

    val loadedFromIndexCache: Boolean
        get() = lock.read { cachedIndex }

    /** Segments hashed by the last open; trusted segments verified earlier in this process. */
    val segmentsHashed: Int
        get() = lock.read { hashed }

    val segmentCount: Int
        get() = lock.read { channels.size }

    fun append(layers: List<TileLayer>): AppendResult = lock.write {
        if (layers.isEmpty()) return AppendResult(0, 0, 0, 0)
        validateEpochs(layers)
        val normalized =
            LayerNormalizer.normalize(layers) { key ->
                latestTiles[key] ?: index[key]?.let { read(key, it.lastEpoch)?.colors }
            }
        val writing = normalized.layers
        if (writing.isEmpty()) return AppendResult(0, layers.size, 0, 0)
        val (sealed, written, size) = seal(writing)
        val channel = FileChannel.open(sealed, StandardOpenOption.READ)
        val segmentId = channels.size
        channels += channel
        segmentFiles += TileIndexCache.Segment(sealed, size)
        for (entry in written) {
            index
                .getOrPut(entry.key, PackedTileHistory::forAppend)
                .add(
                    entry.epoch,
                    segmentId,
                    entry.offset,
                    entry.length,
                    entry.coverage,
                    entry.kind,
                )
        }
        records += writing.size
        latest = maxOf(latest, writing.maxOf(TileLayer::epoch))
        bytes += size
        latestTiles.putAll(normalized.latest)
        cacheDirty = true
        logger.debug(
            "Appended {} layers ({} bytes) to {} as segment {}",
            writing.size,
            size,
            directory.fileName,
            segmentId,
        )
        AppendResult(
            writing.size,
            layers.size - writing.size,
            writing.sumOf { it.colors.size },
            size,
        )
    }

    /** Rejects the whole batch before anything is written when tile epochs do not increase. */
    fun validateEpochs(layers: List<TileLayer>): Unit = lock.read {
        val lastInBatch = HashMap<TileKey, Long>()
        for (layer in layers) {
            val previous = lastInBatch[layer.key] ?: index[layer.key]?.lastEpoch ?: -1L
            require(layer.epoch > previous) { "Tile epochs must increase for ${layer.key}" }
            lastInBatch[layer.key] = layer.epoch
        }
    }

    private data class Sealed(val path: Path, val records: List<WrittenRecord>, val size: Long)

    private fun seal(writing: List<TileLayer>): Sealed {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".palimpsest-", ".tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val written = ArrayList<WrittenRecord>(writing.size)
            val groups =
                writing.groupBy(TileLayer::key).toSortedMap(compareBy(TileKey::z, TileKey::x))
            var offset = MAGIC.size.toLong() + Int.SIZE_BYTES
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { output ->
                write(output, MAGIC, digest)
                write(output, leInt(groups.size), digest)
                for ((key, history) in groups) {
                    val groupHeader =
                        ByteBuffer.allocate(12)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(key.x)
                            .putInt(key.z)
                            .putInt(history.size)
                            .array()
                    write(output, groupHeader, digest)
                    offset += groupHeader.size
                    var previousEpoch = 0L
                    for (layer in history) {
                        val body = AdaptiveLayerCodec.encode(layer, layer.epoch - previousEpoch)
                        write(output, body, digest)
                        written +=
                            WrittenRecord(
                                key,
                                offset,
                                body.size,
                                body[0].toInt() and 255,
                                layer.epoch,
                                layer.coverage.copyOf(),
                            )
                        offset += body.size
                        previousEpoch = layer.epoch
                    }
                }
                output.force(true)
            }
            val name = digest.digest().joinToString("") { "%02x".format(it) } + EXTENSION
            val sealed = directory.resolve(name)
            if (Files.exists(sealed)) throw IOException("Segment already exists: $sealed")
            Files.move(temporary, sealed, StandardCopyOption.ATOMIC_MOVE)
            return Sealed(sealed, written, offset)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun read(key: TileKey, epoch: Long): TileRead? = lock.read {
        val history = index[key] ?: return null
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
            val history = index[key] ?: return null
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
        val channel = channels[history.segmentAt(index)]
        val kind = history.kindAt(index)
        if (kind == AdaptiveLayerCodec.FULL_EXCEPTIONS) {
            val body = ByteBuffer.allocate(length)
            readFully(channel, history.offsetAt(index), body)
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
        readFully(channel, history.offsetAt(index) + firstOffset, payloadBytes)
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
        val history = index[key] ?: return false
        history.firstAfter(minOf(firstEpoch, secondEpoch)) !=
            history.firstAfter(maxOf(firstEpoch, secondEpoch))
    }

    /** Persists a dirty index sidecar without closing; safe to call from a background tick. */
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
                    .filter { it.fileName.toString().endsWith(EXTENSION) }
                    .sorted()
                    .map { TileIndexCache.Segment(it, Files.size(it)) }
                    .toList()
            }
        try {
            if (!(indexCacheEnabled && files.isNotEmpty() && loadCachedIndex(files))) {
                files.forEach(::indexSegment)
                index.values.forEach(PackedTileHistory::finishReload)
                cacheDirty = true
                persistIndexCache()
            }
        } catch (failure: Throwable) {
            resetIndex()
            throw failure
        }
        logger.debug(
            "Opened {}: {} segments, {} tiles, {} layers in {} ms (index cache {}, {} hashed)",
            directory.fileName,
            channels.size,
            index.size,
            records,
            (System.nanoTime() - start) / 1_000_000,
            if (cachedIndex) "hit" else "miss",
            hashed,
        )
    }

    private fun resetIndex() {
        channels.forEach(FileChannel::close)
        channels.clear()
        segmentFiles.clear()
        index.clear()
        latestTiles.clear()
        bytes = 0
        records = 0
        latest = 0
        cachedIndex = false
        cacheDirty = false
        hashed = 0
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadCachedIndex(files: List<TileIndexCache.Segment>): Boolean {
        val cache = TileIndexCache.path(directory)
        try {
            val loaded =
                TileIndexCache.load(cache, files) {
                    key,
                    epoch,
                    segment,
                    offset,
                    length,
                    coverage,
                    kind ->
                    addIndexedLayer(key, epoch, segment, offset, length, coverage, kind)
                }
            if (!loaded) {
                resetIndex()
                return false
            }
            index.values.forEach(PackedTileHistory::finishReload)
            for (file in files) openSegment(file.path, file.size)
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
        if (!cacheDirty) return
        cacheDirty = false
        if (!indexCacheEnabled) return
        val cache = TileIndexCache.path(directory)
        try {
            if (records == 0) {
                Files.deleteIfExists(cache)
                return
            }
            val start = System.nanoTime()
            TileIndexCache.save(cache, segmentFiles, index)
            logger.debug(
                "Saved index cache for {} ({} layers) in {} ms",
                directory.fileName,
                records,
                (System.nanoTime() - start) / 1_000_000,
            )
        } catch (failure: Exception) {
            logger.warn("Could not save disposable index cache {}: {}", cache, failure.toString())
        }
    }

    private fun indexSegment(file: TileIndexCache.Segment) {
        val segmentId = channels.size
        openSegment(file.path, file.size) { channel ->
            indexAdaptiveSegment(channel, file.path, file.size, segmentId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun openSegment(file: Path, size: Long, index: (FileChannel) -> Unit = {}) {
        val channel = FileChannel.open(file, StandardOpenOption.READ)
        try {
            if (SegmentVerifier.verify(channel, file, size, MAGIC)) hashed++
            index(channel)
            bytes += size
            channels += channel
            segmentFiles += TileIndexCache.Segment(file, size)
        } catch (failure: Throwable) {
            channel.close()
            throw failure
        }
    }

    @Suppress("ThrowsCount")
    private fun indexAdaptiveSegment(channel: FileChannel, file: Path, size: Long, segmentId: Int) {
        if (size < MAGIC.size + Int.SIZE_BYTES)
            throw CorruptHistoryException("Truncated segment $file")
        val count = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, MAGIC.size.toLong(), count)
        val groupCount = count.getInt(0)
        if (groupCount < 1 || groupCount > (size - 12) / 15) {
            throw CorruptHistoryException("Invalid tile group count in $file")
        }
        var offset = 12L
        repeat(groupCount) {
            if (offset + 12 > size) throw CorruptHistoryException("Truncated tile group in $file")
            val group = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            readFully(channel, offset, group)
            val key = TileKey(group.getInt(0), group.getInt(4))
            val recordCount = group.getInt(8)
            offset += 12
            if (recordCount < 1 || recordCount > (size - offset) / 3) {
                throw CorruptHistoryException("Invalid tile record count in $file")
            }
            var previousEpoch = 0L
            val prefix = ByteArray(43)
            repeat(recordCount) { position ->
                val available = minOf(prefix.size.toLong(), size - offset).toInt()
                readFully(channel, offset, ByteBuffer.wrap(prefix, 0, available))
                val length = AdaptiveLayerCodec.recordLength(prefix, available)
                if (length !in 3..AdaptiveLayerCodec.MAX_BYTES || offset + length > size) {
                    throw CorruptHistoryException("Invalid adaptive record in $file")
                }
                val body = ByteArray(length)
                val copied = minOf(available, length)
                prefix.copyInto(body, endIndex = copied)
                if (copied < length) {
                    readFully(
                        channel,
                        offset + copied,
                        ByteBuffer.wrap(body, copied, length - copied),
                    )
                }
                val metadata = AdaptiveLayerCodec.indexMetadata(body, previousEpoch)
                if (position > 0 && metadata.epoch <= previousEpoch) {
                    throw CorruptHistoryException("Non-increasing tile epoch in $file")
                }
                addIndexedLayer(
                    key,
                    metadata.epoch,
                    segmentId,
                    offset,
                    length,
                    metadata.coverage,
                    body[0].toInt() and 255,
                )
                previousEpoch = metadata.epoch
                offset += length
            }
        }
        if (offset != size)
            throw CorruptHistoryException("Unexpected bytes after tile groups in $file")
    }

    private fun addIndexedLayer(
        key: TileKey,
        epoch: Long,
        segmentId: Int,
        offset: Long,
        length: Int,
        coverage: LongArray,
        kind: Int,
    ) {
        index
            .getOrPut(key, PackedTileHistory::forReload)
            .add(epoch, segmentId, offset, length, coverage, kind)
        records++
        latest = maxOf(latest, epoch)
    }

    private fun readLayer(key: TileKey, history: PackedTileHistory, index: Int): TileLayer {
        val channel = channels[history.segmentAt(index)]
        val body = ByteBuffer.allocate(history.lengthAt(index))
        readFully(channel, history.offsetAt(index), body)
        return AdaptiveLayerCodec.decode(body.array(), key, history.epochAt(index))
    }

    override fun close(): Unit = lock.write {
        persistIndexCache()
        channels.forEach(FileChannel::close)
        channels.clear()
        latestTiles.clear()
    }

    private companion object {
        val MAGIC = "PALIMPSC".toByteArray(Charsets.US_ASCII)
        const val EXTENSION = ".pseg"

        fun leInt(value: Int): ByteArray =
            ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

        fun write(channel: FileChannel, bytes: ByteArray, digest: MessageDigest) {
            digest.update(bytes)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
        }

        fun readFully(channel: FileChannel, position: Long, buffer: ByteBuffer) {
            var offset = position
            while (buffer.hasRemaining()) {
                val count = channel.read(buffer, offset)
                if (count <= 0) throw IOException("Unexpected end of segment")
                offset += count
            }
        }
    }
}
