package io.github.fopwoc.mods.palimpsest.tree

import io.github.fopwoc.mods.framework.log.logger
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * The map as a persistent quadtree over tiles. Every commit path-copies from the changed tiles up
 * to a new root and shares everything else, so the history is the list of roots: reading the map at
 * any moment is picking a root and descending, and comparing two moments descends only where the
 * two trees stop sharing nodes. Nodes carry a sample per child, so a far zoom reads nodes and never
 * opens a tile.
 *
 * Tile coordinates are signed chunk coordinates; inside the tree they are offset to unsigned
 * [LEVELS]-bit numbers, and a node at level `k` covers `2^k` tiles per side. Level 0 is the tile. A
 * root names the smallest square holding everything seen so far, so the empty levels above the
 * explored world are not stored; reads treat them as single-child squares.
 */
class MapTree(
    directory: Path,
    private val machineId: Int,
    sealBytes: Int = SegmentSet.DEFAULT_SEAL_BYTES,
    nodeCacheSize: Int = 65_536,
    tileCacheSize: Int = 4_096,
    private val translateBlock: (machine: Int, id: Int) -> Int = { _, id -> id },
) : AutoCloseable {
    private val logger = logger<MapTree>()
    /**
     * Newest committed epoch, for new segments' base epoch; kept apart from [roots] so it exists
     * before them.
     */
    private val latestKnown = AtomicLong(0)
    private val segments = SegmentSet(directory, machineId, sealBytes, latestKnown::get)
    private val nodes = RecordCache<NodeRecord>(nodeCacheSize)
    private val tiles = RecordCache<TileRecord>(tileCacheSize)
    /** Deltas written since a tile's last full record; a miss means "write a full record". */
    private val deltaDepth =
        object : LinkedHashMap<TileKey, Int>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, Int>): Boolean =
                size > DELTA_DEPTHS
        }
    private val nodesRead = AtomicLong()
    private val tilesDecoded = AtomicLong()
    /**
     * Facts hash → the full tile record holding those facts, so identical tiles are stored once.
     */
    private val content = LongLongMap()

    @Volatile
    var roots: RootIndex = RootIndex(segments.roots())
        private set

    class CommitResult(
        val tilesWritten: Int,
        val nodesWritten: Int,
        val nodesPatched: Int,
        val tilesLinked: Int,
        val bytes: Int,
    )

    init {
        latestKnown.set(roots.latestEpoch.coerceAtLeast(0))
        loadContent()
        logger.info(
            "Map tree at {}: {} roots, latest epoch {}",
            directory,
            roots.size,
            roots.latestEpoch,
        )
    }

    val latestEpoch: Long
        get() = roots.latestEpoch

    /** Distinct full tile records known for deduplication. */
    val contentSize: Int
        get() = content.size

    private fun loadContent() {
        for (index in 0 until segments.size) {
            when (val reader = segments.reader(index)) {
                is SegmentReader.Sealed ->
                    for (entry in reader.trailer.content) content.put(
                        entry.hash,
                        Ref(index, entry.offset).packed,
                    )
                is SegmentWriter ->
                    for (offset in reader.replayedFullTiles) content.put(
                        tile(Ref(index, offset)).factsHash(),
                        Ref(index, offset).packed,
                    )
            }
        }
    }

    fun nodesRead(): Long = nodesRead.get()

    fun tilesDecoded(): Long = tilesDecoded.get()

    // ---- writing ----

    /**
     * Records the given tiles as of [epoch], which must be after every earlier commit. A tile whose
     * facts equal its current version is skipped. If every tile is unchanged, nothing is written
     * and the current root remains latest.
     */
    @Synchronized
    fun commit(epoch: Long, changes: Map<TileKey, TileRecord>): CommitResult {
        require(epoch > roots.latestEpoch) { "Epoch $epoch not after ${roots.latestEpoch}" }
        require(changes.values.all { it.epoch == epoch }) {
            "Every record must carry the commit epoch"
        }
        val changed = changes.filter { (key, record) ->
            tile(key, Long.MAX_VALUE)?.sameFacts(record) != true
        }
        if (changed.isEmpty()) return CommitResult(0, 0, 0, 0, 0)
        val writer = segments.active
        val segment = segments.activeSegment
        val refs = segments.refs(segment)
        val counts = IntArray(4)
        val before = writer.size
        writer.beginGroup()
        val previous = roots.latest
        val top = coveringSquare(previous, changed.keys)
        val existing = if (top.level == previous.level) previous.ref else Ref.NULL
        val written =
            if (existing.isNull && top.level != previous.level && !previous.ref.isNull) {
                // The world grew: the old root becomes a descendant of the new top square.
                insert(
                    lift(previous, top, writer, segment, refs, counts),
                    top.level,
                    top.x,
                    top.z,
                    changed.entries.toList(),
                    epoch,
                    writer,
                    segment,
                    refs,
                    counts,
                )
            } else {
                insert(
                    existing,
                    top.level,
                    top.x,
                    top.z,
                    changed.entries.toList(),
                    epoch,
                    writer,
                    segment,
                    refs,
                    counts,
                )
            }
        val root = RootRecord(epoch, top.level, top.x, top.z, written.ref)
        writer.root(root, refs)
        writer.commitGroup()
        roots = roots.with(root)
        latestKnown.set(epoch)
        return CommitResult(counts[0], counts[1], counts[2], counts[3], writer.size - before)
    }

    private class Square(val level: Int, val x: Int, val z: Int)

    /** The smallest square that holds the previous root's square and every changed tile. */
    private fun coveringSquare(previous: RootRecord, keys: Collection<TileKey>): Square {
        if (keys.isEmpty()) return Square(previous.level, previous.x, previous.z)
        var level = 0
        var minX = keys.minOf(::unsignedX)
        var maxX = keys.maxOf(::unsignedX)
        var minZ = keys.minOf(::unsignedZ)
        var maxZ = keys.maxOf(::unsignedZ)
        if (!previous.ref.isNull) {
            minX = minOf(minX, previous.x shl previous.level)
            maxX = maxOf(maxX, ((previous.x + 1) shl previous.level) - 1)
            minZ = minOf(minZ, previous.z shl previous.level)
            maxZ = maxOf(maxZ, ((previous.z + 1) shl previous.level) - 1)
        }
        while (
            level < LEVELS &&
                ((minX ushr level) != (maxX ushr level) || (minZ ushr level) != (maxZ ushr level))
        ) level++
        // A root is never a bare tile; the top square is at least one level up.
        level = maxOf(level, 1)
        return Square(level, minX ushr level, minZ ushr level)
    }

    /**
     * Wraps the old root in single-child nodes up to just below [top]; returns the node ref at
     * top's level.
     */
    @Suppress("LongParameterList")
    private fun lift(
        previous: RootRecord,
        top: Square,
        writer: SegmentWriter,
        segment: Int,
        refs: RefCoder,
        counts: IntArray,
    ): Ref {
        var ref = previous.ref
        var sample = node(ref).sample
        var level = previous.level
        var x = previous.x
        var z = previous.z
        while (level < top.level) {
            // Square coordinates, so the quarter within the parent is their lowest bit.
            val quarter = NodeRecord.quarter(x, z, 1)
            val node = NodeRecord.single(quarter, ref, sample, node(ref).maxEpoch)
            ref = writeNode(node, Ref.NULL, writer, segment, refs, counts)
            level++
            x = x ushr 1
            z = z ushr 1
        }
        return ref
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
        val base = if (existing.isNull) NodeRecord.EMPTY else node(existing)
        var node = base
        val byQuarter = changes.groupBy { quarterOf(it.key, level) }
        var changed = false
        for ((quarter, subset) in byQuarter) {
            val childX = nodeX * 2 + (quarter and 1)
            val childZ = nodeZ * 2 + (quarter shr 1)
            val child = node.child(quarter)
            val written =
                insert(
                    child,
                    level - 1,
                    childX,
                    childZ,
                    subset,
                    epoch,
                    writer,
                    segment,
                    refs,
                    counts,
                )
            if (written.ref != child) {
                node = node.with(quarter, written.ref, written.sample, epoch)
                changed = true
            }
        }
        if (!changed) return Written(existing, node.sample)
        val ref = writeNode(node, existing, writer, segment, refs, counts)
        return Written(ref, node.sample)
    }

    @Suppress("LongParameterList")
    private fun writeNode(
        node: NodeRecord,
        baseRef: Ref,
        writer: SegmentWriter,
        segment: Int,
        refs: RefCoder,
        counts: IntArray,
    ): Ref {
        val sink = ByteSink()
        val base = if (baseRef.isNull) null else node(baseRef)
        val asPatch = base != null && NodeCodec.encodePatch(sink, node, base, baseRef, refs)
        if (!asPatch) {
            sink.clear()
            NodeCodec.encodeFull(sink, node, refs)
        }
        val bytes = sink.toByteArray()
        val offset = writer.record(SegmentFormat.RecordType.NODE) { it.bytes(bytes) }
        counts[1]++
        if (asPatch) counts[2]++
        val ref = Ref(segment, offset)
        nodes.put(
            ref,
            if (asPatch) node.withPatchDepth(checkNotNull(base).patchDepth + 1)
            else node.withPatchDepth(0),
        )
        return ref
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
        // A link to identical facts already on disk beats any delta or full record.
        val hash = record.factsHash()
        val same = content.get(hash)?.let { Ref(it) }?.takeIf { tile(it).sameFacts(record) }
        val linked = same != null
        var asDelta = false
        when {
            same != null -> TileCodec.encodeLink(sink, record, previous, same, refs)
            base != null &&
                depth < MAX_DELTA_CHAIN &&
                TileCodec.encodeDelta(sink, record, base, previous, refs) -> asDelta = true
            else -> {
                sink.clear()
                TileCodec.encodeFull(sink, record, previous, refs)
            }
        }
        val bytes = sink.toByteArray()
        val offset = writer.record(SegmentFormat.RecordType.TILE) { it.bytes(bytes) }
        if (!asDelta && !linked) {
            content.put(hash, Ref(segment, offset).packed)
            writer.content(hash, offset)
        }
        if (linked) counts[3]++
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
    fun tileRef(key: TileKey, root: RootRecord): Ref {
        if (root.ref.isNull) return Ref.NULL
        if (squareX(key, root.level) != root.x || squareZ(key, root.level) != root.z)
            return Ref.NULL
        var ref = root.ref
        var level = root.level
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
        if (root.ref.isNull) return out
        if (level >= root.level) {
            // The window asks for squares at or above the root: the root's square is the only one.
            val x = root.x ushr (level - root.level)
            val z = root.z ushr (level - root.level)
            if (x - x0 in 0 until side && z - z0 in 0 until side)
                out[(z - z0) * side + (x - x0)] = node(root.ref).sample.packed
            return out
        }
        if (intersects(root.x, root.z, root.level, level, x0, z0, side))
            collectSamples(root.ref, root.level, root.x, root.z, level, x0, z0, side, out)
        return out
    }

    @Suppress("LongParameterList")
    private fun collectSamples(
        ref: Ref,
        level: Int,
        nodeX: Int,
        nodeZ: Int,
        target: Int,
        x0: Int,
        z0: Int,
        side: Int,
        out: LongArray,
    ) {
        val node = node(ref)
        if (level == target + 1) {
            for (quarter in 0 until NodeRecord.QUARTERS) {
                val x = nodeX * 2 + (quarter and 1) - x0
                val z = nodeZ * 2 + (quarter shr 1) - z0
                if (x in 0 until side && z in 0 until side)
                    out[z * side + x] = node.sample(quarter).packed
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

    /**
     * Whether the level-[level] square at (x, z) overlaps the window given in level-[target]
     * squares.
     */
    @Suppress("LongParameterList")
    internal fun intersects(
        x: Int,
        z: Int,
        level: Int,
        target: Int,
        x0: Int,
        z0: Int,
        side: Int,
    ): Boolean {
        if (level < target) {
            val shift = target - level
            val sx = x ushr shift
            val sz = z ushr shift
            return sx - x0 in 0 until side && sz - z0 in 0 until side
        }
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
        TreeDiff(
                this,
                DiffWindow(level, x0, z0, side) { x, z -> out[(z - z0) * side + (x - x0)] = true },
            )
            .run(epochA, epochB)
        return out
    }

    /**
     * Every tile that differs between the maps at [epochA] and [epochB], anywhere; stops after
     * [limit] tiles so a huge first commit stays cheap to ask about.
     */
    fun changedTiles(epochA: Long, epochB: Long, limit: Int = Int.MAX_VALUE): List<TileKey> {
        require(limit > 0)
        val out = ArrayList<TileKey>()
        val window =
            DiffWindow(
                level = 0,
                x0 = 0,
                z0 = 0,
                side = 1 shl LEVELS,
                full = { out.size >= limit },
            ) { x, z ->
                out += TileKey(x - OFFSET, z - OFFSET)
            }
        TreeDiff(this, window).run(epochA, epochB)
        return out
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
            if (record.type != SegmentFormat.RecordType.NODE)
                throw CorruptTreeException("$ref is a ${record.type}, expected a node")
            val decoded = NodeCodec.decode(record.source, segments.refs(ref.segment))
            val node = decoded.node ?: decoded.apply(node(decoded.base))
            if (reader.machineId == machineId) node
            else node.mapBlocks { translateBlock(reader.machineId, it) }
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
        if (record.type != SegmentFormat.RecordType.TILE)
            throw CorruptTreeException("$ref is a ${record.type}, expected a tile")
        val decoded = TileCodec.decode(record.source, segments.refs(ref.segment))
        return if (reader.machineId == machineId) decoded
        else decoded.mapBlocks { translateBlock(reader.machineId, it) }
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
        logger.info(
            "Map tree closed: {} nodes read, {} tiles decoded",
            nodesRead.get(),
            tilesDecoded.get(),
        )
    }

    companion object {
        /** Whole-world level: 2^22 tiles per side, offset so chunk coordinates in ±2^21 fit. */
        const val LEVELS = 22
        const val OFFSET = 1 shl (LEVELS - 1)
        private const val MAX_DELTA_CHAIN = 16
        private const val DELTA_DEPTHS = 1 shl 18

        fun unsignedX(key: TileKey): Int = key.x + OFFSET

        fun unsignedZ(key: TileKey): Int = key.z + OFFSET

        /** Level-[level] square containing the tile, in that level's coordinates. */
        fun squareX(key: TileKey, level: Int): Int = unsignedX(key) ushr level

        fun squareZ(key: TileKey, level: Int): Int = unsignedZ(key) ushr level

        private fun quarterOf(key: TileKey, level: Int): Int =
            NodeRecord.quarter(unsignedX(key), unsignedZ(key), level)
    }
}
