package io.github.fopwoc.mods.palimpsest.tree

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import kotlin.io.path.name

/** One replaceable full record per chunk, with an in-memory sample pyramid for distant zoom. */
class LatestTileStore(
    val directory: Path,
    private val machineId: Int,
    private val translateBlock: (machine: Int, id: Int) -> Int = { _, id -> id },
) : TileSource {
    private data class Entry(val machine: Int, val epoch: Long, val sample: Sample, val path: Path)

    private data class Representative(val tile: TileKey, val sample: Sample)

    private val entries = ConcurrentHashMap<TileKey, Entry>()
    private val samples = List(MapTree.LEVELS) { ConcurrentHashMap<Long, Representative>() }
    private val lock: FileLock

    @Volatile
    override var latestEpoch: Long = -1
        private set

    init {
        Files.createDirectories(directory)
        val ignore = directory.resolve(".gitignore")
        if (!Files.exists(ignore)) Files.writeString(ignore, "latest.lock\n*.tmp\n")
        val channel =
            FileChannel.open(
                directory.resolve("latest.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        val acquired =
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
        if (acquired == null) {
            channel.close()
            throw MapInUseException("$directory is open in another game")
        }
        lock = acquired
        loadEntries()
    }

    @Suppress("TooGenericExceptionCaught") // Any failed load must release the acquired lock.
    private fun loadEntries() {
        try {
            Files.walk(directory).use { files ->
                files
                    .filter { it.name.endsWith(SUFFIX) }
                    .forEach { path ->
                        val key = parseKey(path)
                        val entry = readHeader(path)
                        entries[key] = entry
                        remember(key, entry.sample)
                        latestEpoch = maxOf(latestEpoch, entry.epoch)
                    }
            }
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    override fun tile(key: TileKey, epoch: Long): TileRecord? {
        require(epoch == Long.MAX_VALUE) { "Latest-only storage has no historical snapshots" }
        val entry = entries[key] ?: return null
        DataInputStream(Files.newInputStream(entry.path)).use { input ->
            val header = readHeader(input, entry.path)
            val expected = input.readInt().toLong() and 0xFFFFFFFFL
            val bytes = input.readNBytes(TileRecord.PIXELS * BYTES_PER_PIXEL)
            if (
                bytes.size != TileRecord.PIXELS * BYTES_PER_PIXEL ||
                    CRC32().apply { update(bytes) }.value != expected
            )
                throw CorruptTreeException("Invalid latest tile ${entry.path}")
            DataInputStream(bytes.inputStream()).use { payload ->
                val blocks = ShortArray(TileRecord.PIXELS)
                val heights = ByteArray(TileRecord.PIXELS)
                val depths = ByteArray(TileRecord.PIXELS)
                val biomes = ShortArray(TileRecord.PIXELS)
                for (pixel in 0 until TileRecord.PIXELS) {
                    blocks[pixel] =
                        translateBlock(header.machine, payload.readUnsignedShort()).toShort()
                    heights[pixel] = payload.readByte()
                    depths[pixel] = payload.readByte()
                    biomes[pixel] = payload.readShort()
                }
                return TileRecord(header.epoch, blocks, heights, depths, biomes)
            }
        }
    }

    override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray {
        require(epoch == Long.MAX_VALUE && level in 0 until MapTree.LEVELS && side > 0)
        val levelSamples = samples[level]
        return LongArray(side * side) { offset ->
            levelSamples[squareId(x0 + offset % side, z0 + offset / side)]?.sample?.packed
                ?: Sample.NONE.packed
        }
    }

    fun representative(level: Int, x: Int, z: Int): Pair<TileKey, Sample>? =
        samples[level][squareId(x, z)]?.let { it.tile to it.sample }

    override fun representativeTile(level: Int, x: Int, z: Int): TileKey? =
        representative(level, x, z)?.first

    fun keys(): Set<TileKey> = entries.keys.toSet()

    fun diskBytes(): Long = entries.values.sumOf { Files.size(it.path) }

    @Synchronized
    fun clear() {
        for (entry in entries.values) Files.deleteIfExists(entry.path)
        entries.clear()
        samples.forEach(MutableMap<Long, Representative>::clear)
        latestEpoch = -1
    }

    @Synchronized
    override fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int {
        require(epoch > latestEpoch)
        var changed = 0
        for ((key, record) in changes) {
            require(record.epoch == epoch)
            if (tile(key, Long.MAX_VALUE)?.sameFacts(record) == true) continue
            val path = path(key)
            Files.createDirectories(path.parent)
            val temporary = Files.createTempFile(path.parent, "tile-", ".tmp")
            try {
                val payload = ByteArrayOutputStream(TileRecord.PIXELS * BYTES_PER_PIXEL)
                DataOutputStream(payload).use { output ->
                    for (pixel in 0 until TileRecord.PIXELS) {
                        output.writeShort(record.block(pixel))
                        output.writeByte(record.height(pixel))
                        output.writeByte(record.depth(pixel))
                        output.writeShort(record.biome(pixel))
                    }
                }
                val bytes = payload.toByteArray()
                DataOutputStream(Files.newOutputStream(temporary)).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(machineId)
                    output.writeLong(epoch)
                    output.writeLong(record.sample.packed)
                    output.writeInt(CRC32().apply { update(bytes) }.value.toInt())
                    output.write(bytes)
                }
                FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                Files.deleteIfExists(temporary)
            }
            val entry = Entry(machineId, epoch, record.sample, path)
            entries[key] = entry
            remember(key, entry.sample)
            changed++
        }
        latestEpoch = epoch
        return changed
    }

    private fun remember(key: TileKey, sample: Sample) {
        for (level in samples.indices) {
            val square = squareId(MapTree.squareX(key, level), MapTree.squareZ(key, level))
            samples[level].compute(square) { _, current ->
                if (
                    current == null ||
                        key == current.tile ||
                        key.z < current.tile.z ||
                        key.z == current.tile.z && key.x < current.tile.x
                )
                    Representative(key, sample)
                else current
            }
        }
    }

    private fun path(key: TileKey): Path =
        directory
            .resolve("r${Math.floorDiv(key.x, REGION)}_${Math.floorDiv(key.z, REGION)}")
            .resolve("${key.x}_${key.z}$SUFFIX")

    private fun parseKey(path: Path): TileKey {
        val parts = path.name.removeSuffix(SUFFIX).split('_')
        if (parts.size != 2) throw CorruptTreeException("Invalid latest tile name $path")
        return TileKey(parts[0].toInt(), parts[1].toInt())
    }

    private fun readHeader(path: Path): Entry =
        DataInputStream(Files.newInputStream(path)).use { readHeader(it, path) }

    private fun readHeader(input: DataInputStream, path: Path): Entry {
        if (input.readInt() != MAGIC) throw CorruptTreeException("Invalid latest tile $path")
        val machine = input.readInt()
        val epoch = input.readLong()
        val sample = Sample(input.readLong())
        val translated =
            Sample(translateBlock(machine, sample.block), sample.height, sample.depth, sample.biome)
        return Entry(machine, epoch, translated, path)
    }

    override fun sealIfDue(): Boolean = false

    override fun seal() = Unit

    override fun close() {
        lock.channel().close()
    }

    private companion object {
        const val MAGIC = 0x504C5431
        const val BYTES_PER_PIXEL = 6
        const val REGION = 32
        const val SUFFIX = ".tile"

        fun squareId(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}
