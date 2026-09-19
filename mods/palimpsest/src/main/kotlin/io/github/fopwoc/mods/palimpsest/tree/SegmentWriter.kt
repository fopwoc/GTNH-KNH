package io.github.fopwoc.mods.palimpsest.tree

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import org.apache.logging.log4j.LogManager

/**
 * The machine's active segment: records are staged into a group, the group is appended to the file
 * and published in one step, and readers on other threads see either all of a group or none of it.
 * The whole segment also lives in memory (it is a few MB at most), so reads of fresh records never
 * touch the disk and sealing is a hash over the buffer.
 *
 * Opening an existing active file replays its valid groups and truncates a torn tail.
 */
class SegmentWriter(
    private val path: Path,
    override val machineId: Int,
    override val ordinal: Int,
    baseEpoch: Long,
) : SegmentReader(), AutoCloseable {
    override var baseEpoch: Long = baseEpoch
        private set
    /**
     * What readers may see, swapped as one so a reader never pairs a new length with an old array.
     */
    private class Published(val bytes: ByteArray, val length: Int)

    @Volatile private var published = Published(ByteArray(1 shl 16), 0)
    private val slotList = ArrayList<Int>().apply { add(machineId) }
    @Volatile
    override var slots: IntArray = intArrayOf(machineId)
        private set

    private val logger = LogManager.getLogger(SegmentWriter::class.java)
    private val roots = ArrayList<SegmentFormat.RootEntry>()
    private val content = ArrayList<SegmentFormat.ContentEntry>()
    /** Offsets of full tile records replayed from an existing file; their hashes are rebuilt by the tree. */
    val replayedFullTiles = ArrayList<Int>()
    private val channel: FileChannel
    private val group = ByteSink()
    private var groupOpen = false

    /** Roots this segment holds, in write order. */
    val rootEntries: List<SegmentFormat.RootEntry>
        get() = synchronized(roots) { roots.toList() }

    val size: Int
        get() = published.length

    init {
        if (Files.exists(path)) {
            val existing = Files.readAllBytes(path)
            val header = SegmentFormat.readHeader(ByteBuffer.wrap(existing))
            this.baseEpoch = header.baseEpoch
            if (header.machineId != machineId || header.ordinal != ordinal) {
                throw CorruptTreeException(
                    "Active segment belongs to ${MachineId.hex(header.machineId)}#${header.ordinal}"
                )
            }
            val bytes = existing.copyOf(maxOf(existing.size, 1 shl 16))
            published = Published(bytes, existing.size)
            val valid = scan { offset, record -> replay(offset, record) }
            if (valid < existing.size)
                logger.warn(
                    "Truncating {} torn bytes from {}",
                    existing.size - valid,
                    path.fileName,
                )
            published = Published(bytes, valid)
        } else {
            Files.createDirectories(path.parent)
            val header = SegmentFormat.header(machineId, ordinal, baseEpoch)
            val bytes = ByteArray(1 shl 16)
            header.copyInto(bytes)
            published = Published(bytes, header.size)
        }
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        channel.truncate(size.toLong())
        if (channel.size() < size) channel.write(ByteBuffer.wrap(published.bytes, 0, size), 0)
    }

    private fun replay(offset: Int, record: Record) {
        when (record.type) {
            SegmentFormat.RecordType.ROOT ->
                roots += SegmentFormat.RootEntry(record.source.varint(), offset)
            SegmentFormat.RecordType.SLOT -> {
                slotList += record.source.fixed(4).toInt()
                slots = slotList.toIntArray()
            }
            else -> Unit
        }
    }

    override fun view(): Pair<ByteBuffer, Int> = published.let {
        ByteBuffer.wrap(it.bytes) to it.length
    }

    /** Slot of a machine inside this segment, declaring it with a SLOT record on first use. */
    fun slot(machine: Int): Int {
        val existing = slotList.indexOf(machine)
        if (existing >= 0) return existing
        check(groupOpen) { "Slots are declared inside a group" }
        record(SegmentFormat.RecordType.SLOT) { it.fixed(machine.toLong() and 0xFFFFFFFFL, 4) }
        slotList += machine
        slots = slotList.toIntArray()
        return slotList.size - 1
    }

    fun beginGroup() {
        check(!groupOpen)
        group.clear()
        groupOpen = true
    }

    /** Stages one record and returns its offset, valid for refs even before the group commits. */
    fun record(type: SegmentFormat.RecordType, write: (ByteSink) -> Unit): Int {
        check(groupOpen)
        val body = ByteSink().also(write)
        val offset = size + SegmentFormat.FRAME_BYTES + group.size
        group.byte(type.code)
        group.varint(body.size)
        group.bytes(body.toByteArray())
        return offset
    }

    /** Remembers a full tile record's facts hash for the trailer's content table. */
    fun content(hash: Long, offset: Int) {
        content += SegmentFormat.ContentEntry(hash, offset)
    }

    fun root(root: RootRecord, refs: RefCoder): Int {
        val offset = record(SegmentFormat.RecordType.ROOT) { RootRecord.write(it, root, refs) }
        synchronized(roots) { roots += SegmentFormat.RootEntry(root.epoch, offset) }
        return offset
    }

    /** Appends the group to the file and publishes it to readers. */
    fun commitGroup() {
        check(groupOpen)
        groupOpen = false
        if (group.size == 0) return
        val payload = group.toByteArray()
        val framed =
            ByteSink(payload.size + SegmentFormat.FRAME_BYTES + SegmentFormat.CRC_BYTES).apply {
                byte(SegmentFormat.GROUP)
                fixed(payload.size.toLong(), 4)
                bytes(payload)
                fixed(
                    SegmentFormat.crc(ByteBuffer.wrap(payload), 0, payload.size).toLong() and
                        0xFFFFFFFFL,
                    4,
                )
            }
        val block = framed.toByteArray()
        val current = published
        val bytes =
            if (current.length + block.size > current.bytes.size)
                current.bytes.copyOf(maxOf(current.bytes.size * 2, current.length + block.size))
            else current.bytes
        block.copyInto(bytes, current.length)
        channel.write(ByteBuffer.wrap(block), current.length.toLong())
        published = Published(bytes, current.length + block.size)
    }

    /**
     * Writes the trailer, fsyncs, renames the file to its content hash and returns that name. The
     * writer is closed afterwards; the caller opens the sealed file as a [SegmentReader.Sealed].
     */
    fun seal(): String {
        check(!groupOpen)
        val trailer = SegmentFormat.trailer(rootEntries, slotList.drop(1).toIntArray(), content)
        val current = published
        channel.write(ByteBuffer.wrap(trailer), current.length.toLong())
        channel.force(true)
        channel.close()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(current.bytes, 0, current.length)
        digest.update(trailer)
        val name = digest.digest().joinToString("") { "%02x".format(it) } + SEALED_SUFFIX
        Files.move(path, path.resolveSibling(name), StandardCopyOption.ATOMIC_MOVE)
        return name
    }

    override fun close() {
        if (channel.isOpen) {
            channel.force(false)
            channel.close()
        }
    }

    companion object {
        const val SEALED_SUFFIX = ".pseg"
    }
}
