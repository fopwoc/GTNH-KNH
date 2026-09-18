package io.github.fopwoc.mods.palimpsest.storage

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32

/**
 * Disposable record directory keyed by the exact set of immutable segment files.
 *
 * Layout: header (magic, version, segment list), one record block per tile, a tile directory, and a
 * trailer pointing at the directory. Opening reads only the header, directory and trailer; a tile's
 * block is read and checked on demand, so region open cost does not grow with history.
 */
internal class TileIndexCache
private constructor(
    private val channel: FileChannel,
    private val mapped: ByteBuffer,
    val segments: List<Segment>,
    val directory: Map<TileKey, Entry>,
) : AutoCloseable {
    data class Segment(val path: Path, val size: Long)

    /** One tile's block: where it is, how many records it holds and their newest epoch. */
    data class Entry(val records: Int, val lastEpoch: Long, val offset: Long, val length: Int)

    fun interface RecordSink {
        fun record(
            key: TileKey,
            epoch: Long,
            segment: Int,
            offset: Long,
            length: Int,
            coverage: LongArray,
            kind: Int,
        )
    }

    /** Source of one tile's records when saving: already resident or copied from this cache. */
    sealed interface Block {
        val key: TileKey

        class Resident(override val key: TileKey, val history: PackedTileHistory) : Block

        class Cached(override val key: TileKey, val entry: Entry, val cache: TileIndexCache) : Block
    }

    val recordCount: Long
        get() = directory.values.sumOf { it.records.toLong() }

    val latestEpoch: Long
        get() = directory.values.maxOfOrNull(Entry::lastEpoch) ?: 0L

    @Suppress("ThrowsCount")
    fun load(key: TileKey, sink: RecordSink) {
        val entry = directory.getValue(key)
        val block = readBlock(entry)
        val input = DataInputStream(block.inputStream())
        var previousEpoch = 0L
        repeat(entry.records) { recordIndex ->
            val delta = readVarLong(input)
            if (delta > Long.MAX_VALUE - previousEpoch) throw corrupt("epoch overflow")
            val epoch = previousEpoch + delta
            val segmentId = readVarLong(input).toIntExact()
            val offset = readVarLong(input)
            val packedLength = readVarLong(input).toIntExact()
            val length = packedLength ushr 3
            val kind = packedLength and 7
            val mask = readMask(input)
            val invalidEpoch = recordIndex > 0 && epoch <= previousEpoch
            val invalidShape =
                offset < 0 ||
                    length !in 3..AdaptiveLayerCodec.MAX_BYTES ||
                    kind !in AdaptiveLayerCodec.SPARSE..AdaptiveLayerCodec.FULL_EXCEPTIONS ||
                    mask.all { it == 0L }
            val invalidSegment =
                segmentId !in segments.indices || offset > segments[segmentId].size - length
            if (invalidEpoch || invalidShape || invalidSegment) throw corrupt("record directory")
            sink.record(key, epoch, segmentId, offset, length, mask, kind)
            previousEpoch = epoch
        }
        if (previousEpoch != entry.lastEpoch || input.read() != -1) throw corrupt("tile block")
    }

    private fun readBlock(entry: Entry): ByteArray {
        val block = ByteArray(entry.length)
        mapped.get(entry.offset.toInt(), block)
        val crc = CRC32().also { it.update(block) }
        if (mapped.getInt(entry.offset.toInt() + entry.length) != crc.value.toInt()) {
            throw corrupt("tile block checksum")
        }
        return block
    }

    private fun corrupt(what: String) = CorruptHistoryException("Invalid index cache $what")

    override fun close() = channel.close()

    companion object {
        private const val MAGIC = 0x50494458 // PIDX
        private const val VERSION = 5
        private const val FULL_MASK = 0
        private const val RAW_MASK = 9
        private const val TRAILER_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES

        fun path(directory: Path): Path = directory.resolve(".index-cache.pidx")

        /** Returns null when the sidecar does not describe exactly [segments]. */
        @Suppress("ThrowsCount", "ReturnCount", "TooGenericExceptionCaught")
        fun open(file: Path, segments: List<Segment>): TileIndexCache? {
            if (!Files.isRegularFile(file)) return null
            val channel = FileChannel.open(file, StandardOpenOption.READ)
            try {
                val size = channel.size()
                if (size < TRAILER_BYTES + 12)
                    throw CorruptHistoryException("Truncated index cache")
                val trailer = ByteBuffer.allocate(TRAILER_BYTES)
                readFully(channel, size - TRAILER_BYTES, trailer)
                val directoryOffset = trailer.getLong(0)
                val expectedCrc = trailer.getInt(Long.SIZE_BYTES)
                if (trailer.getInt(Long.SIZE_BYTES + Int.SIZE_BYTES) != MAGIC) {
                    throw CorruptHistoryException("Invalid index cache trailer")
                }
                if (directoryOffset !in 12 until size - TRAILER_BYTES) {
                    throw CorruptHistoryException("Invalid index cache directory offset")
                }
                val header = ByteBuffer.allocate(12)
                readFully(channel, 0, header)
                if (header.getInt(0) != MAGIC || header.getInt(4) != VERSION) return null
                val segmentBytes = header.getInt(8)
                val metadata = ByteBuffer.allocate(segmentBytes)
                readFully(channel, 12, metadata)
                val directoryBytes = (size - TRAILER_BYTES - directoryOffset).toInt()
                val directoryBuffer = ByteBuffer.allocate(directoryBytes)
                readFully(channel, directoryOffset, directoryBuffer)
                val crc = CRC32()
                crc.update(header.array())
                crc.update(metadata.array())
                crc.update(directoryBuffer.array())
                if (crc.value.toInt() != expectedCrc) {
                    throw CorruptHistoryException("Index cache checksum mismatch")
                }
                val listed = readSegments(DataInputStream(metadata.array().inputStream()))
                if (listed.size != segments.size) return null
                val actual = segments.associateBy { it.path.fileName.toString() }
                val ordered = listed.map { (name, listedSize) ->
                    val current = actual[name] ?: return null
                    if (current.size != listedSize) return null
                    current
                }
                if (ordered.toSet().size != segments.size) return null
                val directory =
                    readDirectory(DataInputStream(directoryBuffer.array().inputStream()), size)
                if (size > Int.MAX_VALUE) throw CorruptHistoryException("Index cache too large")
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
                return TileIndexCache(channel, mapped, ordered, directory)
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }

        private fun readSegments(input: DataInputStream): List<Pair<String, Long>> {
            val count = input.readInt()
            if (count < 0) throw CorruptHistoryException("Invalid index cache segment count")
            return List(count) { input.readUTF() to input.readLong() }
        }

        private fun readDirectory(input: DataInputStream, fileSize: Long): Map<TileKey, Entry> {
            val count = input.readInt()
            if (count < 0 || count > fileSize / 12) {
                throw CorruptHistoryException("Invalid index cache tile count")
            }
            val directory = HashMap<TileKey, Entry>(count * 2)
            repeat(count) {
                val key = TileKey(input.readInt(), input.readInt())
                val entry =
                    Entry(input.readInt(), input.readLong(), input.readLong(), input.readInt())
                if (
                    entry.records < 1 ||
                        entry.length < 0 ||
                        entry.offset < 12 ||
                        entry.offset + entry.length + Int.SIZE_BYTES > fileSize ||
                        directory.put(key, entry) != null
                ) {
                    throw CorruptHistoryException("Invalid index cache directory")
                }
            }
            return directory
        }

        fun save(file: Path, segments: List<Segment>, blocks: Sequence<Block>) {
            Files.createDirectories(file.parent)
            val temporary = Files.createTempFile(file.parent, ".index-", ".tmp")
            try {
                FileChannel.open(temporary, StandardOpenOption.WRITE).use { output ->
                    val crc = CRC32()
                    val metadata = ByteArrayOutputStream()
                    DataOutputStream(metadata).use { out ->
                        out.writeInt(segments.size)
                        for (segment in segments) {
                            out.writeUTF(segment.path.fileName.toString())
                            out.writeLong(segment.size)
                        }
                    }
                    val header =
                        ByteBuffer.allocate(12)
                            .putInt(MAGIC)
                            .putInt(VERSION)
                            .putInt(metadata.size())
                            .array()
                    crc.update(header)
                    crc.update(metadata.toByteArray())
                    writeFully(output, header)
                    writeFully(output, metadata.toByteArray())
                    var position = 12L + metadata.size()
                    val directory = ByteArrayOutputStream()
                    val directoryOut = DataOutputStream(directory)
                    var tiles = 0
                    for (block in blocks.sortedWith(compareBy({ it.key.z }, { it.key.x }))) {
                        val (bytes, records, lastEpoch) = encode(block)
                        val blockCrc = CRC32().also { it.update(bytes) }
                        writeFully(output, bytes)
                        writeFully(
                            output,
                            ByteBuffer.allocate(4).putInt(blockCrc.value.toInt()).array(),
                        )
                        directoryOut.writeInt(block.key.x)
                        directoryOut.writeInt(block.key.z)
                        directoryOut.writeInt(records)
                        directoryOut.writeLong(lastEpoch)
                        directoryOut.writeLong(position)
                        directoryOut.writeInt(bytes.size)
                        position += bytes.size + Int.SIZE_BYTES
                        tiles++
                    }
                    directoryOut.flush()
                    val directoryBytes =
                        ByteBuffer.allocate(4 + directory.size())
                            .putInt(tiles)
                            .put(directory.toByteArray())
                            .array()
                    crc.update(directoryBytes)
                    writeFully(output, directoryBytes)
                    writeFully(
                        output,
                        ByteBuffer.allocate(TRAILER_BYTES)
                            .putLong(position)
                            .putInt(crc.value.toInt())
                            .putInt(MAGIC)
                            .array(),
                    )
                    output.force(true)
                }
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                Files.deleteIfExists(temporary)
            }
        }

        private data class Encoded(val bytes: ByteArray, val records: Int, val lastEpoch: Long)

        private fun encode(block: Block): Encoded =
            when (block) {
                is Block.Cached ->
                    Encoded(
                        block.cache.readBlock(block.entry),
                        block.entry.records,
                        block.entry.lastEpoch,
                    )
                is Block.Resident -> {
                    val history = block.history
                    val bytes = ByteArrayOutputStream()
                    DataOutputStream(bytes).use { out ->
                        var previousEpoch = 0L
                        for (index in 0 until history.size) {
                            val epoch = history.epochAt(index)
                            writeVarLong(out, epoch - previousEpoch)
                            writeVarLong(out, history.segmentAt(index).toLong())
                            writeVarLong(out, history.offsetAt(index))
                            writeVarLong(
                                out,
                                (history.lengthAt(index).toLong() shl 3) or
                                    history.kindAt(index).toLong(),
                            )
                            writeMask(out, history, index)
                            previousEpoch = epoch
                        }
                    }
                    Encoded(bytes.toByteArray(), history.size, history.lastEpoch)
                }
            }

        private fun writeMask(output: DataOutputStream, history: PackedTileHistory, index: Int) {
            val count =
                (0 until TileLayer.MASK_WORDS).sumOf {
                    java.lang.Long.bitCount(history.maskAt(index, it))
                }
            when {
                count == TileLayer.PIXELS -> output.writeByte(FULL_MASK)
                count <= 8 -> {
                    output.writeByte(count)
                    for (position in 0 until TileLayer.PIXELS) {
                        if (
                            history.maskAt(index, position ushr 6) and (1L shl (position and 63)) !=
                                0L
                        ) {
                            output.writeByte(position)
                        }
                    }
                }
                else -> {
                    output.writeByte(RAW_MASK)
                    for (word in 0 until TileLayer.MASK_WORDS) output.writeLong(
                        history.maskAt(index, word)
                    )
                }
            }
        }

        private fun readMask(input: DataInputStream): LongArray {
            val mask = LongArray(TileLayer.MASK_WORDS)
            when (val kind = input.readUnsignedByte()) {
                FULL_MASK -> mask.fill(-1L)
                in 1..8 -> {
                    var previous = -1
                    repeat(kind) {
                        val position = input.readUnsignedByte()
                        if (position <= previous) {
                            throw CorruptHistoryException("Unsorted index mask")
                        }
                        mask[position ushr 6] = mask[position ushr 6] or (1L shl (position and 63))
                        previous = position
                    }
                }
                RAW_MASK -> for (word in mask.indices) mask[word] = input.readLong()
                else -> throw CorruptHistoryException("Invalid index mask kind $kind")
            }
            return mask
        }

        private fun writeVarLong(output: DataOutputStream, value: Long) {
            require(value >= 0)
            var remaining = value
            while (remaining >= 128) {
                output.writeByte(((remaining and 127) or 128).toInt())
                remaining = remaining ushr 7
            }
            output.writeByte(remaining.toInt())
        }

        private fun readVarLong(input: DataInputStream): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                val byte = input.readUnsignedByte()
                if (shift == 63 && byte > 0) throw CorruptHistoryException("Index varint overflow")
                value = value or ((byte and 127).toLong() shl shift)
                if (byte and 128 == 0) return value
            }
            throw CorruptHistoryException("Index varint too long")
        }

        private fun Long.toIntExact(): Int {
            if (this > Int.MAX_VALUE) throw CorruptHistoryException("Index integer overflow")
            return toInt()
        }

        private fun readFully(channel: FileChannel, position: Long, buffer: ByteBuffer) {
            var offset = position
            while (buffer.hasRemaining()) {
                val count = channel.read(buffer, offset)
                if (count <= 0) throw IOException("Unexpected end of index cache")
                offset += count
            }
        }

        private fun writeFully(channel: FileChannel, bytes: ByteArray) {
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
        }
    }
}
