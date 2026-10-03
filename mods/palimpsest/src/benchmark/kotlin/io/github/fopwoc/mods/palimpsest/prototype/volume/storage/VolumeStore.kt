package io.github.fopwoc.mods.palimpsest.prototype.volume.storage

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource
import io.github.fopwoc.mods.palimpsest.tree.ChannelCodec
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * Full 3D history as two append-only files. `sections.pack` holds encoded sections and column
 * biome grids, each stored once per distinct content. `chunks.log` holds, per commit, the chunks
 * that changed and, for each, only its changed slots: 16 sections and the biome grid. A version is
 * therefore 17 references, most inherited, and reading any moment is picking a chunk's version and
 * decoding the sections it names.
 *
 * Prototype limits: the index lives in memory and is not rebuilt on reopen; identity is a 64-bit
 * content hash checked by a second 32-bit hash rather than by bytes.
 */
class VolumeStore(directory: Path, cacheEntries: Int = 16_384) : AutoCloseable {
    /** [packed] is the new pack bytes each chunk caused; content shared with earlier chunks costs 0. */
    class Commit(val chunks: Int, val written: Int, val reused: Int, val bytes: Long, val packed: Map<TileKey, Int>)

    private class History {
        var epochs = LongArray(2)
        var refs = arrayOfNulls<LongArray>(2)
        var size = 0
        val latestHashes = LongArray(SLOTS) { EMPTY }

        fun add(epoch: Long, slots: LongArray) {
            if (size == epochs.size) {
                epochs = epochs.copyOf(size * 2)
                refs = refs.copyOf(size * 2)
            }
            epochs[size] = epoch
            refs[size++] = slots
        }

        fun at(epoch: Long): LongArray? {
            var low = 0
            var high = size - 1
            var found = -1
            while (low <= high) {
                val middle = (low + high) ushr 1
                if (epochs[middle] <= epoch) {
                    found = middle
                    low = middle + 1
                } else high = middle - 1
            }
            return if (found < 0) null else refs[found]
        }
    }

    private class Blob(val ref: Long, val check: Int)

    private val pack = FileChannel.open(directory.resolve("sections.pack"), CREATE, READ, WRITE, TRUNCATE_EXISTING)
    private val log = FileChannel.open(directory.resolve("chunks.log"), CREATE, WRITE, TRUNCATE_EXISTING)
    private val blobs = HashMap<Long, Blob>()
    private val histories = HashMap<TileKey, History>()
    private val cache =
        object : LinkedHashMap<Long, IntArray>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, IntArray>) =
                size > cacheEntries
        }
    private var latestEpoch = Long.MIN_VALUE

    var packBytes = 0L
        private set

    var logBytes = 0L
        private set

    val bytes: Long
        get() = packBytes + logBytes

    val chunks: Int
        get() = histories.size

    fun commit(epoch: Long, volumes: Map<TileKey, ChunkVolume>): Commit {
        require(epoch > latestEpoch)
        latestEpoch = epoch
        val packed = ByteSink(64 * 1024)
        val entries = ByteSink(4096)
        var changedChunks = 0
        var written = 0
        var reused = 0
        val perChunk = HashMap<TileKey, Int>()
        for ((key, volume) in volumes) {
            val packedBefore = packed.size
            val history = histories.getOrPut(key, ::History)
            val previous = history.at(Long.MAX_VALUE)
            val slots = previous?.copyOf() ?: LongArray(SLOTS)
            var mask = 0
            for (slot in 0 until SLOTS) {
                val content = if (slot < BIOMES) volume.section(slot) else volume.biomes()
                val hash = content?.let { hash(it, slot == BIOMES) } ?: EMPTY
                if (hash == history.latestHashes[slot] && previous != null) continue
                history.latestHashes[slot] = hash
                mask = mask or (1 shl slot)
                if (content == null) {
                    slots[slot] = 0
                    continue
                }
                val check = content.contentHashCode()
                val known = blobs[hash]?.takeIf { it.check == check }
                if (known != null) {
                    slots[slot] = known.ref
                    reused++
                    continue
                }
                val encoded = if (slot < BIOMES) SectionCodec.encode(content) else encodeBiomes(content)
                val ref = ((packBytes + packed.size) shl LENGTH_BITS) or encoded.size.toLong()
                packed.bytes(encoded)
                blobs[hash] = Blob(ref, check)
                slots[slot] = ref
                written++
            }
            if (mask == 0) continue
            perChunk[key] = packed.size - packedBefore
            changedChunks++
            history.add(epoch, slots)
            entries.signed(key.x.toLong())
            entries.signed(key.z.toLong())
            entries.varint(mask)
            for (slot in 0 until SLOTS) if (mask and (1 shl slot) != 0) entries.varint(slots[slot])
        }
        if (changedChunks == 0) return Commit(0, 0, 0, 0, emptyMap())
        val header = ByteSink(16).apply {
            varint(epoch)
            varint(changedChunks)
        }
        val before = bytes
        packBytes += write(pack, packBytes, packed.toByteArray())
        logBytes += write(log, logBytes, header.toByteArray() + entries.toByteArray())
        return Commit(changedChunks, written, reused, bytes - before, perChunk)
    }

    /** The chunk as it was at [epoch], or null if it had not been seen yet. */
    fun volume(key: TileKey, epoch: Long): ChunkVolume? {
        val slots = histories[key]?.at(epoch) ?: return null
        val sections = Array(BIOMES) { slot -> slots[slot].takeIf { it != 0L }?.let(::section) }
        return ChunkVolume(sections, blob(slots[BIOMES], ::decodeBiomes))
    }

    fun keys(): Set<TileKey> = histories.keys

    fun clearCache() = synchronized(cache) { cache.clear() }

    private fun section(ref: Long): IntArray = blob(ref, SectionCodec::decode)

    private fun blob(ref: Long, decode: (ByteArray) -> IntArray): IntArray {
        synchronized(cache) { cache[ref] }?.let { return it }
        val bytes = ByteBuffer.allocate((ref and LENGTH_MASK).toInt())
        var position = ref ushr LENGTH_BITS
        while (bytes.hasRemaining()) position += pack.read(bytes, position)
        val decoded = decode(bytes.array())
        synchronized(cache) { cache[ref] = decoded }
        return decoded
    }

    private fun encodeBiomes(biomes: IntArray): ByteArray =
        ByteSink(64).also { ChannelCodec.encode(it, biomes, 2) }.toByteArray()

    private fun decodeBiomes(bytes: ByteArray): IntArray =
        ChannelCodec.decode(ByteSource(bytes), ChunkVolume.COLUMNS, 2)

    private fun write(channel: FileChannel, at: Long, bytes: ByteArray): Int {
        val buffer = ByteBuffer.wrap(bytes)
        var position = at
        while (buffer.hasRemaining()) position += channel.write(buffer, position)
        return bytes.size
    }

    override fun close() {
        pack.close()
        log.close()
    }

    private companion object {
        const val BIOMES = ChunkVolume.SECTIONS
        const val SLOTS = BIOMES + 1
        const val LENGTH_BITS = 24
        const val LENGTH_MASK = (1L shl LENGTH_BITS) - 1
        /** The hash of an absent section; no content hashes to it (see [hash]). */
        const val EMPTY = 0L

        fun hash(values: IntArray, biomes: Boolean): Long {
            var hash = if (biomes) -0x61c8864680b583ebL else 0x2545F4914F6CDD1DL
            for (value in values) {
                hash = (hash xor value.toLong()) * -0x40a7b892e31b1a47L
                hash = hash xor (hash ushr 29)
            }
            return if (hash == EMPTY) 1 else hash
        }
    }
}
