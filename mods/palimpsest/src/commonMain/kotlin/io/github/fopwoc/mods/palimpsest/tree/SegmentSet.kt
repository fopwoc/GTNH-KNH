package io.github.fopwoc.mods.palimpsest.tree

import io.github.fopwoc.mods.framework.log.logger
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.name

/**
 * Every segment of one slice directory, by runtime index: the sealed ones of every machine (from
 * `segments.<machine>.txt` manifests, one line per ordinal) and this machine's active one. A ref's
 * segment number is an index into this table; it is stable for the life of the process.
 *
 * Only sealed `.pseg` files and manifests are map data; the active file and `.machine` stay local.
 * A sealed file that no manifest lists (crash between rename and manifest append) is adopted on
 * open if it is ours and next in line.
 */
class SegmentSet(
    val directory: Path,
    val machineId: Int,
    private val sealBytes: Int = DEFAULT_SEAL_BYTES,
    /**
     * Base epoch for a new active segment: the latest commit epoch, so record epochs stay small.
     */
    private val baseEpoch: () -> Long = { 0L },
) : AutoCloseable {
    class Handle(val machineId: Int, val ordinal: Int, val name: String?) {
        @Volatile var reader: SegmentReader? = null
    }

    private val logger = logger<SegmentSet>()
    private val lock = ReentrantReadWriteLock()
    private val handles = ArrayList<Handle>()
    private val byIdentity = HashMap<Long, Int>()
    private var activeIndex = -1
    private var writer: SegmentWriter? = null

    init {
        Files.createDirectories(directory)
        val ignore = directory.resolve(".gitignore")
        if (!Files.exists(ignore)) Files.writeString(ignore, "active-*\n*.tmp\n")
        val manifests =
            Files.list(directory).use { files ->
                files
                    .filter {
                        it.name.startsWith(MANIFEST_PREFIX) && it.name.endsWith(MANIFEST_SUFFIX)
                    }
                    .toList()
            }
        for (manifest in manifests.sortedBy { it.name }) {
            val machine =
                manifest.name
                    .removePrefix(MANIFEST_PREFIX)
                    .removeSuffix(MANIFEST_SUFFIX)
                    .toLong(16)
                    .toInt()
            Files.readAllLines(manifest)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEachIndexed { ordinal, name ->
                    add(Handle(machine, ordinal, name))
                }
        }
        adoptOrphans()
        openActive()
        logger.info(
            "Segments at {}: {} sealed, machine {}",
            directory,
            handles.size - 1,
            MachineId.hex(machineId),
        )
    }

    private fun add(handle: Handle): Int {
        handles += handle
        byIdentity[identity(handle.machineId, handle.ordinal)] = handles.lastIndex
        return handles.lastIndex
    }

    private fun identity(machine: Int, ordinal: Int): Long =
        (machine.toLong() shl 32) or ordinal.toLong()

    private fun adoptOrphans() {
        val known = handles.mapNotNull(Handle::name).toHashSet()
        val orphans =
            Files.list(directory).use { files ->
                files
                    .filter {
                        it.name.endsWith(SegmentWriter.SEALED_SUFFIX) &&
                            !it.name.startsWith(ACTIVE_PREFIX) &&
                            it.name !in known
                    }
                    .toList()
            }
        for (orphan in orphans.sortedBy { it.name }) {
            val header =
                FileChannel.open(orphan, StandardOpenOption.READ).use {
                    SegmentFormat.readHeader(
                        it.map(
                            FileChannel.MapMode.READ_ONLY,
                            0,
                            minOf(it.size(), SegmentFormat.HEADER_BYTES.toLong()),
                        )
                    )
                }
            if (header.machineId == machineId && header.ordinal == ownSealedCount()) {
                logger.warn("Adopting sealed segment {} missing from the manifest", orphan.name)
                appendManifest(orphan.name)
                add(Handle(machineId, header.ordinal, orphan.name))
            } else {
                logger.warn(
                    "Ignoring segment {} of {}#{} that no manifest lists",
                    orphan.name,
                    MachineId.hex(header.machineId),
                    header.ordinal,
                )
            }
        }
    }

    private fun ownSealedCount(): Int = handles.count {
        it.machineId == machineId && it.name != null
    }

    private fun manifest(): Path =
        directory.resolve("$MANIFEST_PREFIX${MachineId.hex(machineId)}$MANIFEST_SUFFIX")

    private fun appendManifest(name: String) {
        Files.writeString(
            manifest(),
            name + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    private fun openActive() {
        val ordinal = ownSealedCount()
        val active =
            SegmentWriter(
                directory.resolve(
                    "$ACTIVE_PREFIX${MachineId.hex(machineId)}${SegmentWriter.SEALED_SUFFIX}"
                ),
                machineId,
                ordinal,
                baseEpoch(),
            )
        writer = active
        activeIndex = add(Handle(machineId, ordinal, null).also { it.reader = active })
    }

    val active: SegmentWriter
        get() = checkNotNull(writer)

    /** Runtime index of the active segment; refs to freshly written records use it. */
    val activeSegment: Int
        get() = lock.read { activeIndex }

    val size: Int
        get() = lock.read { handles.size }

    fun handle(index: Int): Handle = lock.read { handles[index] }

    fun indexOf(machine: Int, ordinal: Int): Int = lock.read {
        byIdentity[identity(machine, ordinal)] ?: -1
    }

    /** The reader of a segment, mapping a sealed file on first use. */
    fun reader(index: Int): SegmentReader {
        val handle = handle(index)
        handle.reader?.let {
            return it
        }
        synchronized(handle) {
            handle.reader?.let {
                return it
            }
            val path = directory.resolve(checkNotNull(handle.name))
            val mapped =
                FileChannel.open(path, StandardOpenOption.READ).use {
                    it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())
                }
            val header = SegmentFormat.readHeader(mapped)
            if (header.machineId != handle.machineId || header.ordinal != handle.ordinal) {
                throw CorruptTreeException(
                    "${handle.name} is ${MachineId.hex(header.machineId)}#${header.ordinal}, manifest says ${MachineId.hex(handle.machineId)}#${handle.ordinal}"
                )
            }
            val trailer =
                SegmentFormat.readTrailer(mapped)
                    ?: throw CorruptTreeException("${handle.name} has no trailer")
            return SegmentReader.Sealed(mapped, header, trailer).also { handle.reader = it }
        }
    }

    fun refs(index: Int): RefCoder = SlotRefCoder(this, index, reader(index))

    /** Roots of every segment with their runtime refs, unsorted. */
    fun roots(): List<RootRecord> =
        (0 until size).flatMap { index ->
            val reader = reader(index)
            val refs = refs(index)
            val entries =
                if (reader is SegmentWriter) reader.rootEntries
                else (reader as SegmentReader.Sealed).trailer.roots
            entries.map { entry -> RootRecord.read(reader.record(entry.offset).source, refs) }
        }

    /** Seals the active segment when it grew past the threshold; true when it did. */
    fun sealIfDue(): Boolean {
        if (active.size < sealBytes) return false
        seal()
        return true
    }

    /** Seals the active segment (if it holds anything) and starts a new one. */
    fun seal() {
        val current = active
        if (current.size <= SegmentFormat.HEADER_BYTES) return
        val name = current.seal()
        appendManifest(name)
        lock.write {
            val handle = handles[activeIndex]
            handles[activeIndex] = Handle(handle.machineId, handle.ordinal, name)
            openActive()
        }
        logger.debug("Sealed segment {}", name)
    }

    override fun close() {
        writer?.close()
    }

    companion object {
        const val DEFAULT_SEAL_BYTES = 4 shl 20
        const val MANIFEST_PREFIX = "segments."
        const val MANIFEST_SUFFIX = ".txt"
        const val ACTIVE_PREFIX = "active-"
    }
}
