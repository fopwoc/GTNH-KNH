package io.github.fopwoc.mods.palimpsest.tree

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.storage.StorageWrites
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.zip.CRC32
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Indexed, compressed current tiles. Appends are crash-framed; compaction replaces the file. */
internal class LatestRegion(
    val path: Path,
    private val writes: StorageWrites,
) : AutoCloseable {
    data class Entry(
        val machine: Int,
        val epoch: Long,
        val sample: Sample,
        val offset: Int,
        val length: Int,
        val crc: Int,
    )

    private val logger = logger<LatestRegion>()
    private val lock = ReentrantReadWriteLock()
    private val index = arrayOfNulls<Entry>(SIDE * SIDE)
    private var size = HEADER_BYTES
    private var channel: FileChannel? = null
    var unflushedBytes: Long = 0
        private set

    init {
        if (Files.exists(path)) load()
    }

    fun entries(): List<Pair<Int, Entry>> = lock.read {
        index.mapIndexedNotNull { slot, entry -> entry?.let { slot to it } }
    }

    fun tile(slot: Int, translate: (Int, Int) -> Int): TileRecord? = lock.read {
        val entry = index[slot] ?: return@read null
        val source = ByteSource(readPayload(entry))
        val channels =
            TileRecord.Channel.entries.map {
                ChannelCodec.decode(source, TileRecord.PIXELS, it.bytes)
            }
        if (source.remaining != 0) throw CorruptTreeException("Trailing tile bytes in $path")
        TileRecord(
            entry.epoch,
            ShortArray(TileRecord.PIXELS) { translate(entry.machine, channels[0][it]).toShort() },
            ByteArray(TileRecord.PIXELS) { channels[1][it].toByte() },
            ByteArray(TileRecord.PIXELS) { channels[2][it].toByte() },
            ShortArray(TileRecord.PIXELS) { channels[3][it].toShort() },
        )
    }

    fun write(machine: Int, changes: Map<Int, TileRecord>, flushEachBatch: Boolean) = lock.write {
        val payload = ByteSink()
        for ((slot, record) in changes) {
            val encoded = ByteSink()
            for (channel in TileRecord.Channel.entries) ChannelCodec.encode(
                encoded,
                record.channel(channel),
                channel.bytes,
            )
            appendRecord(payload, slot, machine, record.epoch, record.sample, encoded.toByteArray())
        }
        val output =
            channel
                ?: FileChannel.open(
                        path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                    )
                    .also { channel = it }
        if (output.size() < HEADER_BYTES) {
            writeFully(output, ByteBuffer.wrap(header()), 0, StorageWrites.Kind.CURRENT_APPEND)
            unflushedBytes += HEADER_BYTES
        }
        val framed = frame(payload.toByteArray())
        writeFully(
            output,
            ByteBuffer.wrap(framed),
            size.toLong(),
            StorageWrites.Kind.CURRENT_APPEND,
        )
        output.truncate((size + framed.size).toLong())
        unflushedBytes += framed.size
        if (flushEachBatch) flush()
        applyGroup(payload.toByteArray(), size + FRAME_BYTES)
        size += framed.size
        val live = index.filterNotNull().sumOf { it.length + RECORD_OVERHEAD }
        if (size > maxOf(MIN_COMPACT_BYTES, live * 2)) compact()
    }

    fun flush(): Boolean = lock.write {
        val output = channel ?: return@write false
        output.force(true)
        writes.flushed()
        output.close()
        channel = null
        unflushedBytes = 0
        true
    }

    override fun close() {
        try {
            flush()
        } finally {
            channel?.close()
        }
    }

    fun delete() = lock.write {
        channel?.close()
        channel = null
        unflushedBytes = 0
        Files.deleteIfExists(path)
        index.fill(null)
        size = HEADER_BYTES
    }

    private fun load() {
        val bytes = Files.readAllBytes(path)
        if (
            bytes.size < HEADER_BYTES || !bytes.copyOfRange(0, HEADER_BYTES).contentEquals(header())
        )
            throw CorruptTreeException("Invalid current region $path")
        var position = HEADER_BYTES
        while (position + FRAME_BYTES + CRC_BYTES <= bytes.size) {
            val source = ByteSource(bytes, position)
            val length = source.fixed(FRAME_BYTES).toInt()
            if (length < 0 || length > bytes.size - position - FRAME_BYTES - CRC_BYTES) break
            val payload = source.bytes(length)
            if (crc(payload) != source.fixed(CRC_BYTES).toInt()) break
            applyGroup(payload, position + FRAME_BYTES)
            position = source.position
        }
        size = position
        // Only a complete checksummed group can publish entries after a crash.
        if (position != bytes.size) {
            logger.warn(
                "Discarding {} incomplete bytes from current region {}",
                bytes.size - position,
                path.fileName,
            )
            FileChannel.open(path, StandardOpenOption.WRITE).use { it.truncate(position.toLong()) }
        }
    }

    private fun applyGroup(payload: ByteArray, origin: Int) {
        val source = ByteSource(payload)
        val pending = ArrayList<Pair<Int, Entry>>()
        while (source.remaining > 0) {
            val slot = source.fixed(2).toInt()
            if (slot !in index.indices) throw CorruptTreeException("Invalid region tile $slot")
            val machine = source.fixed(4).toInt()
            val epoch = source.fixed(8)
            if (epoch < 0) throw CorruptTreeException("Invalid region epoch $epoch")
            val sample = Sample.read(source)
            val length = source.varintInt()
            val crc = source.fixed(4).toInt()
            val offset = origin + source.position
            source.skip(length)
            pending += slot to Entry(machine, epoch, sample, offset, length, crc)
        }
        for ((slot, entry) in pending) index[slot] = entry
    }

    private fun readPayload(entry: Entry): ByteArray {
        val bytes = ByteArray(entry.length)
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            var offset = entry.offset.toLong()
            while (buffer.hasRemaining()) {
                val count = channel.read(buffer, offset)
                if (count <= 0) throw CorruptTreeException("Truncated current region $path")
                offset += count
            }
        }
        if (crc(bytes) != entry.crc) throw CorruptTreeException("Invalid tile checksum in $path")
        return bytes
    }

    private fun compact() {
        val current = Files.readAllBytes(path)
        val payload = ByteSink()
        for ((slot, entry) in index.withIndex()) {
            if (entry != null)
                appendRecord(
                    payload,
                    slot,
                    entry.machine,
                    entry.epoch,
                    entry.sample,
                    current.copyOfRange(entry.offset, entry.offset + entry.length).also {
                        if (crc(it) != entry.crc)
                            throw CorruptTreeException("Invalid tile checksum in $path")
                    },
                )
        }
        val bytes = payload.toByteArray()
        val temporary = Files.createTempFile(path.parent, "region-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                writeFully(
                    channel,
                    ByteBuffer.wrap(header()),
                    0,
                    StorageWrites.Kind.CURRENT_COMPACTION,
                )
                writeFully(
                    channel,
                    ByteBuffer.wrap(frame(bytes)),
                    HEADER_BYTES.toLong(),
                    StorageWrites.Kind.CURRENT_COMPACTION,
                )
                channel.force(true)
                writes.flushed()
            }
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            channel?.close()
            channel = null
            unflushedBytes = 0
            writes.compacted()
            index.fill(null)
            applyGroup(bytes, HEADER_BYTES + FRAME_BYTES)
            size = HEADER_BYTES + FRAME_BYTES + bytes.size + CRC_BYTES
            logger.debug(
                "Compacted current region {}: {} -> {} bytes",
                path.fileName,
                current.size,
                size,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun appendRecord(
        sink: ByteSink,
        slot: Int,
        machine: Int,
        epoch: Long,
        sample: Sample,
        bytes: ByteArray,
    ) {
        sink.fixed(slot.toLong(), 2)
        sink.fixed(machine.toLong() and 0xFFFFFFFFL, 4)
        sink.fixed(epoch, 8)
        Sample.write(sink, sample)
        sink.varint(bytes.size)
        sink.fixed(crc(bytes).toLong() and 0xFFFFFFFFL, 4)
        sink.bytes(bytes)
    }

    private fun frame(payload: ByteArray): ByteArray =
        ByteSink()
            .apply {
                fixed(payload.size.toLong(), FRAME_BYTES)
                bytes(payload)
                fixed(crc(payload).toLong() and 0xFFFFFFFFL, CRC_BYTES)
            }
            .toByteArray()

    private fun header(): ByteArray = CurrentRegionFormat.MAGIC

    private fun crc(bytes: ByteArray): Int = CRC32().apply { update(bytes) }.value.toInt()

    private fun writeFully(
        channel: FileChannel,
        buffer: ByteBuffer,
        from: Long,
        kind: StorageWrites.Kind,
    ) {
        var offset = from
        while (buffer.hasRemaining()) {
            val count = channel.write(buffer, offset)
            check(count > 0) { "No progress writing $path" }
            writes.written(kind, count.toLong())
            offset += count
        }
    }

    companion object {
        const val SIDE = 32
        private const val HEADER_BYTES = CurrentRegionFormat.HEADER_BYTES
        private const val FRAME_BYTES = 4
        private const val CRC_BYTES = 4
        private const val RECORD_OVERHEAD = 27
        private const val MIN_COMPACT_BYTES = 4096
    }
}
