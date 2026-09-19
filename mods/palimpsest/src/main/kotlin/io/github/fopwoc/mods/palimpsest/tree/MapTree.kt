package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import org.apache.logging.log4j.LogManager

/**
 * The map as a persistent quadtree over tiles. Every commit path-copies from the changed tiles up
 * to a new root and shares everything else, so the history is the list of roots: reading the map
 * at any moment is picking a root and descending, and comparing two moments descends only where
 * the two trees stop sharing nodes. Nodes carry a sample per child, so a far zoom reads nodes and
 * never opens a tile.
 *
 * Tile coordinates are signed chunk coordinates; inside the tree they are offset to unsigned
 * [LEVELS]-bit numbers, and a node at level `k` covers `2^k` tiles per side. Level 0 is the tile.
 */
class MapTree(
    directory: Path,
    private val machineId: Int,
    sealBytes: Int = SegmentSet.DEFAULT_SEAL_BYTES,
    nodeCacheSize: Int = 65_536,
    tileCacheSize: Int = 4_096,
    private val translateBlock: (machine: Int, id: Int) -> Int = { _, id -> id },
) : AutoCloseable {
    private val logger = LogManager.getLogger(MapTree::class.java)
    private val segments = SegmentSet(directory, machineId, sealBytes)
    private val nodes = RecordCache<NodeRecord>(nodeCacheSize)
    private val tiles = RecordCache<TileRecord>(tileCacheSize)
    /** Deltas written since a tile's last full record; a miss means "write a full record". */
    private val deltaDepth =
        object : LinkedHashMap<TileKey, Int>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, Int>): Boolean = size > DELTA_DEPTHS
        }
    private val nodesRead = AtomicLong()
    private val tilesDecoded = AtomicLong()

    @Volatile var roots: RootIndex = RootIndex(segments.roots())
        private set

    class CommitResult(val tilesWritten: Int, val nodesWritten: Int, val bytes: Int)

    init {
        logger.info("Map tree at {}: {} roots, latest epoch {}", directory, roots.size, roots.latestEpoch)
    }

    val latestEpoch: Long
        get() = roots.latestEpoch

    fun nodesRead(): Long = nodesRead.get()

    fun tilesDecoded(): Long = tilesDecoded.get()

    // ---- writing ----

    /**
     * Records the given tiles as of [epoch], which must be after every earlier commit. A tile whose
     * facts equal its current version is skipped; the commit is one root even when nothing changed.
     */
    @Synchronized
    fun commit(epoch: Long, changes: Map<TileKey, TileRecord>): CommitResult {
        require(epoch > roots.latestEpoch) { "Epoch $epoch not after ${roots.latestEpoch}" }
        require(changes.values.all { it.epoch == epoch }) { "Every record must carry the commit epoch" }
        val writer = segments.active
        val segment = segments.activeSegment
        val refs = segments.refs(segment)
        val counts = IntArray(2)
        val before = writer.size
        writer.beginGroup()
        val root = insert(roots.latest, LEVELS, 0, 0, changes.entries.toList(), epoch, writer, segment, refs, counts)
        writer.root(epoch, root.ref, refs)
        writer.commitGroup()
        roots = roots.with(epoch, root.ref)
        return CommitResult(counts[0], counts[1], writer.size - before)
    }

    private class Written(val ref: Ref, val sample: Sample)

    @Suppress("LongParameterList")
    private fun insert(
        existing: Ref,
        level: Int,
        nodeX: Int,
        nodeZ: Int,
        changes: List<Map.Entry<TileKey, TileRecord>>,
        epoch: Long,
        writer: SegmentWriter,
        segment: Int,
        refs: RefCoder,
        counts: IntArray,
    ): Written {
        if (level == 0) {
            val (key, record) = changes.single()
            return writeTile(key, record, existing, writer, segment, refs, counts)
                ?: Written(existing, tile(existing).sample)
        }
        var node = if (existing.isNull) NodeRecord.EMPTY else node(existing)
        val byQuarter = changes.groupBy { quarterOf(it.key, level) }
        var changed = false
        for ((quarter, subset) in byQuarter) {
            val childX = nodeX * 2 + (quarter and 1)
            val childZ = nodeZ * 2 + (quarter shr 1)
            val child = node.child(quarter)
            val written = insert(child, level - 1, childX, childZ, subset, epoch, writer, segment, refs, counts)
            if (written.ref != child) {
                node = node.with(quarter, written.ref, written.sample, epoch)
                changed = true
            }
        }
        if (!changed) return Written(existing, node.sample)
        val offset = writer.record(SegmentFormat.RecordType.NODE) { NodeCodec.encode(it, node, refs) }
        counts[1]++
        val ref = Ref(segment, offset)
        nodes.put(ref, node)
        return Written(ref, node.sample)
    }

    @Suppress("LongParameterList")
    private fun writeTile(
        key: TileKey,
        record: TileRecord,
        previous: Ref,
        writer: SegmentWriter,
        segment: Int,
        refs: RefCoder,
        counts: IntArray,
    ): Written? {
        val base = if (previous.isNull) null else tile(previous)
        if (base != null && base.sameFacts(record)) return null
        val depth = deltaDepth[key] ?: 0
        val sink = ByteSink()
        val asDelta =
            base != null && depth < MAX_DELTA_CHAIN && TileCodec.encodeDelta(sink, record, base, previous, refs)
        if (!asDelta) {
            sink.clear()
            TileCodec.encodeFull(sink, record, previous, refs)
        }
        val bytes = sink.toByteArray()
        val offset = writer.record(SegmentFormat.RecordType.TILE) { it.bytes(bytes) }
        deltaDepth[key] = if (asDelta) depth + 1 else 0
        counts[0]++
        val ref = Ref(segment, offset)
        tiles.put(ref, record)
        return Written(ref, record.sample)
    }

    // ---- reading ----

    /** The tile as of [epoch] (`Long.MAX_VALUE` for the latest), or null if never seen by then. */
    fun tile(key: TileKey, epoch: Long): TileRecord? {
        val ref = tileRef(key, roots.rootAt(epoch))
        return if (ref.isNull) null else tile(ref)
    }

    /** Where the tile's record lives under [root]; null ref if absent. */
    fun tileRef(key: TileKey, root: Ref): Ref {
        var ref = root
        var level = LEVELS
        while (!ref.isNull && level > 0) {
            ref = node(ref).child(quarterOf(key, level))
            level--
        }
        return ref
    }

    /**
     * Samples of the level-[level] squares in the given window (`side` squares per side, from
     * square `(x0, z0)` in that level's coordinates), row-major, [Sample.NONE] where nothing was
     * ever seen. Read from the parents' sample blocks, never from tiles.
     */
    fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray {
        require(level in 0 until LEVELS && side > 0)
        val out = LongArray(side * side) { Sample.NONE.packed }
        val root = roots.rootAt(epoch)
        if (root.isNull) return out
        collectSamples(root, LEVELS, 0, 0, level, x0, z0, side, out)
        return out
    }

    @Suppress("LongParameterList")
    private fun collectSamples(ref: Ref, level: Int, nodeX: Int, nodeZ: Int, target: Int, x0: Int, z0: Int, side: Int, out: LongArray) {
        val node = node(ref)
        if (level == target + 1) {
            for (quarter in 0 until NodeRecord.QUARTERS) {
                val x = nodeX * 2 + (quarter and 1) - x0
                val z = nodeZ * 2 + (quarter shr 1) - z0
                if (x in 0 until side && z in 0 until side) out[z * side + x] = node.sample(quarter).packed
            }
            return
        }
        for (quarter in 0 until NodeRecord.QUARTERS) {
            val child = node.child(quarter)
            if (child.isNull) continue
            val childX = nodeX * 2 + (quarter and 1)
            val childZ = nodeZ * 2 + (quarter shr 1)
            if (intersects(childX, childZ, level - 1, target, x0, z0, side)) {
                collectSamples(child, level - 1, childX, childZ, target, x0, z0, side, out)
            }
        }
    }

    /** Whether the level-[level] square at (x, z) overlaps the window given in level-[target] squares. */
    @Suppress("LongParameterList")
    private fun intersects(x: Int, z: Int, level: Int, target: Int, x0: Int, z0: Int, side: Int): Boolean {
        val shift = level - target
        val fromX = x.toLong() shl shift
        val fromZ = z.toLong() shl shift
        val size = 1L shl shift
        return fromX < x0 + side && fromX + size > x0 && fromZ < z0 + side && fromZ + size > z0
    }

    /**
     * Which level-[level] squares in the window differ between the maps at [epochA] and [epochB]:
     * row-major booleans. Descends only into subtrees the two roots do not share.
     */
    fun changed(epochA: Long, epochB: Long, level: Int, x0: Int, z0: Int, side: Int): BooleanArray {
        require(level in 0..LEVELS && side > 0)
        val out = BooleanArray(side * side)
        diff(roots.rootAt(epochA), roots.rootAt(epochB), LEVELS, 0, 0, level, x0, z0, side, out)
        return out
    }

    @Suppress("LongParameterList")
    private fun diff(a: Ref, b: Ref, level: Int, nodeX: Int, nodeZ: Int, target: Int, x0: Int, z0: Int, side: Int, out: BooleanArray) {
        if (a == b) return
        if (!intersects(nodeX, nodeZ, level, target, x0, z0, side)) return
        if (level == target) {
            out[(nodeZ - z0) * side + (nodeX - x0)] = true
            return
        }
        if (a.isNull || b.isNull) {
            markPresent(if (a.isNull) b else a, level, nodeX, nodeZ, target, x0, z0, side, out)
            return
        }
        val nodeA = node(a)
        val nodeB = node(b)
        for (quarter in 0 until NodeRecord.QUARTERS) {
            diff(nodeA.child(quarter), nodeB.child(quarter), level - 1, nodeX * 2 + (quarter and 1), nodeZ * 2 + (quarter shr 1), target, x0, z0, side, out)
        }
    }

    /** One side has nothing here: every square present on the other side changed. */
    @Suppress("LongParameterList")
    private fun markPresent(ref: Ref, level: Int, nodeX: Int, nodeZ: Int, target: Int, x0: Int, z0: Int, side: Int, out: BooleanArray) {
        if (!intersects(nodeX, nodeZ, level, target, x0, z0, side)) return
        if (level == target) {
            out[(nodeZ - z0) * side + (nodeX - x0)] = true
            return
        }
        val node = node(ref)
        for (quarter in 0 until NodeRecord.QUARTERS) {
            val child = node.child(quarter)
            if (child.isNull) continue
            markPresent(child, level - 1, nodeX * 2 + (quarter and 1), nodeZ * 2 + (quarter shr 1), target, x0, z0, side, out)
        }
    }

    /** Every version of a tile, newest first, following the previous-version links. */
    fun history(key: TileKey): Sequence<TileRecord> = sequence {
        var ref = tileRef(key, roots.latest)
        while (!ref.isNull) {
            yield(tile(ref))
            ref = previousOf(ref)
        }
    }

    fun node(ref: Ref): NodeRecord =
        nodes.getOrLoad(ref) {
            nodesRead.incrementAndGet()
            val reader = segments.reader(ref.segment)
            val record = reader.record(ref.offset)
            if (record.type != SegmentFormat.RecordType.NODE) throw CorruptTreeException("$ref is a ${record.type}, expected a node")
            NodeCodec.decode(record.source, segments.refs(ref.segment)).let { node ->
                if (reader.machineId == machineId) node else node.mapBlocks { translateBlock(reader.machineId, it) }
            }
        }

    fun tile(ref: Ref): TileRecord =
        tiles.getOrLoad(ref) {
            tilesDecoded.incrementAndGet()
            val decoded = decodeTile(ref)
            decoded.record ?: decoded.apply(tile(decoded.base))
        }

    private fun decodeTile(ref: Ref): TileCodec.Decoded {
        val reader = segments.reader(ref.segment)
        val record = reader.record(ref.offset)
        if (record.type != SegmentFormat.RecordType.TILE) throw CorruptTreeException("$ref is a ${record.type}, expected a tile")
        val decoded = TileCodec.decode(record.source, segments.refs(ref.segment))
        return if (reader.machineId == machineId) decoded else decoded.mapBlocks { translateBlock(reader.machineId, it) }
    }

    private fun previousOf(ref: Ref): Ref {
        val record = segments.reader(ref.segment).record(ref.offset)
        return TileCodec.previousOf(record.source, segments.refs(ref.segment))
    }

    // ---- maintenance ----

    /** Seals the active segment when it is large enough; call from a slow tick. */
    @Synchronized fun sealIfDue(): Boolean = segments.sealIfDue()

    /** Seals whatever the active segment holds; for world unload. */
    @Synchronized fun seal() = segments.seal()

    override fun close() {
        segments.close()
        logger.info("Map tree closed: {} nodes read, {} tiles decoded", nodesRead.get(), tilesDecoded.get())
    }

    companion object {
        /** Root level: 2^22 tiles per side, offset so chunk coordinates in ±2^21 fit. */
        const val LEVELS = 22
        const val OFFSET = 1 shl (LEVELS - 1)
        private const val MAX_DELTA_CHAIN = 16
        private const val DELTA_DEPTHS = 1 shl 18

        fun unsignedX(key: TileKey): Int = key.x + OFFSET

        fun unsignedZ(key: TileKey): Int = key.z + OFFSET

        /** Level-[level] square containing the tile, in that level's coordinates. */
        fun squareX(key: TileKey, level: Int): Int = unsignedX(key) ushr level

        fun squareZ(key: TileKey, level: Int): Int = unsignedZ(key) ushr level

        private fun quarterOf(key: TileKey, level: Int): Int = NodeRecord.quarter(unsignedX(key), unsignedZ(key), level)
    }
}
