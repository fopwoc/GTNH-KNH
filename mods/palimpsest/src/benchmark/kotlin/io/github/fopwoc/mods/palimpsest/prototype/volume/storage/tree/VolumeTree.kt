package io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * Generation 2's persistent quadtree carried into 3D. A commit path-copies from the changed chunks
 * to a new root, so every moment is a root and two moments share everything that did not change.
 * Leaves are chunk versions (16 section refs + a 2D summary); sections are full or delta versions.
 * Nodes keep one surface sample per child for far zoom. The root covers the smallest square holding
 * everything seen so far and grows by wrapping itself in parents.
 *
 * Prototype limits: one pack file, roots and writer indexes in memory, no reopen.
 */
class VolumeTree(directory: Path, cacheEntries: Int = 65_536) : AutoCloseable {
    class Root(val epoch: Long, val level: Int, val x: Int, val z: Int, val node: Long)

    class Commit(val chunks: Int, val bytes: Long)

    /** A chunk whose version differs between two moments, and which of its 17 slots differ. */
    class Change(val key: TileKey, val slots: Int)

    private val pack = Pack(directory.resolve("tree.pack"))
    private val sectionCache = RefCache<IntArray>(cacheEntries)
    private val sections = SectionVersions(pack, sectionCache)
    private val summaries = Summaries(pack, RefCache(cacheEntries))
    private val leaves = RefCache<ChunkLeaf>(cacheEntries)
    private val nodes = RefCache<TreeNode>(cacheEntries)
    private val roots = ArrayList<Root>()

    var leafBytes = 0L
        private set
    var nodeBytes = 0L
        private set
    var rootBytes = 0L
        private set
    val nodesVisited = AtomicLong()

    val bytes: Long
        get() = pack.bytes

    fun breakdown(): Map<String, Long> = linkedMapOf(
        "full sections" to sections.fullBytes,
        "section deltas" to sections.deltaBytes,
        "2D summaries" to summaries.bytes,
        "chunk leaves" to leafBytes,
        "tree nodes" to nodeBytes,
        "roots" to rootBytes,
    )

    // ---- writing ----

    fun commit(epoch: Long, volumes: Map<TileKey, ChunkVolume>, surfaces: Map<TileKey, TileRecord>): Commit {
        require(roots.isEmpty() || epoch > roots.last().epoch)
        val before = pack.bytes
        val latest = roots.lastOrNull()
        val changes = HashMap<TileKey, Pair<Long, Long>>()
        for ((key, volume) in volumes) {
            val previousRef = latest?.let { leafRef(it, key) } ?: 0L
            val previous = previousRef.takeIf { it != 0L }?.let(::leaf)
            val slots = LongArray(ChunkLeaf.SLOTS)
            var changed = previous == null
            for (index in 0 until ChunkLeaf.SECTIONS) {
                val prior = previous?.section(index) ?: 0L
                val blocks = volume.section(index)
                if (blocks == null) {
                    changed = changed || prior != 0L
                    continue
                }
                val hash = contentHash(blocks)
                if (prior != 0L && sections.hash(prior) == hash) {
                    slots[index] = prior
                    continue
                }
                slots[index] = sections.write(blocks, hash, prior)
                changed = true
            }
            val surface = surfaces.getValue(key)
            slots[ChunkLeaf.SUMMARY] = summaries.write(surface)
            changed = changed || slots[ChunkLeaf.SUMMARY] != previous?.summary
            if (!changed) continue
            val (record, depth) = ChunkLeaf.encode(epoch, slots, previous, previousRef, pack.next)
            val ref = pack.append(record)
            leafBytes += record.size
            leaves.put(ref, ChunkLeaf(epoch, slots, previousRef, depth))
            changes[key] = ref to surface.sample.packed
        }
        if (changes.isEmpty()) return Commit(0, 0)

        var level = latest?.level ?: 1
        var x = latest?.x ?: (unsigned(changes.keys.first().x) ushr 1)
        var z = latest?.z ?: (unsigned(changes.keys.first().z) ushr 1)
        var node = latest?.node ?: 0L
        while (changes.keys.any { unsigned(it.x) ushr level != x || unsigned(it.z) ushr level != z }) {
            if (node != 0L) {
                val wrapped = TreeNode(LongArray(4), LongArray(4) { Sample.NONE.packed }, 0)
                val quarter = (x and 1) or ((z and 1) shl 1)
                wrapped.children[quarter] = node
                wrapped.samples[quarter] = node(node).representative
                node = writeNode(wrapped, null, 0L)
            }
            level++
            x = x ushr 1
            z = z ushr 1
        }
        node = update(level, x, z, node, changes.keys.toList(), changes).first
        val root = ByteSink(24).apply {
            varint(epoch)
            varint(level)
            varint(x)
            varint(z)
            Pack.writeRef(this, node, pack.next)
        }.toByteArray()
        pack.append(root)
        rootBytes += root.size
        roots += Root(epoch, level, x, z, node)
        pack.flush()
        return Commit(changes.size, pack.bytes - before)
    }

    private fun update(
        level: Int,
        x: Int,
        z: Int,
        ref: Long,
        keys: List<TileKey>,
        changes: Map<TileKey, Pair<Long, Long>>,
    ): Pair<Long, Long> {
        if (level == 0) return changes.getValue(keys.single())
        val base = ref.takeIf { it != 0L }?.let(::node)
        val children = base?.children?.copyOf() ?: LongArray(4)
        val samples = base?.samples?.copyOf() ?: LongArray(4) { Sample.NONE.packed }
        for ((quarter, group) in keys.groupBy { quarter(it, level) }) {
            val childX = (x shl 1) or (quarter and 1)
            val childZ = (z shl 1) or (quarter shr 1)
            val (child, sample) = update(level - 1, childX, childZ, children[quarter], group, changes)
            children[quarter] = child
            samples[quarter] = sample
        }
        val node = TreeNode(children, samples, 0)
        return writeNode(node, base, ref) to node.representative
    }

    private fun writeNode(node: TreeNode, base: TreeNode?, baseRef: Long): Long {
        val (record, depth) = TreeNode.encode(node, base, baseRef, pack.next)
        nodeBytes += record.size
        return pack.append(record).also { nodes.put(it, TreeNode(node.children, node.samples, depth)) }
    }

    // ---- reading ----

    fun rootAt(epoch: Long): Root? {
        var low = 0
        var high = roots.size - 1
        var found: Root? = null
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (roots[middle].epoch <= epoch) {
                found = roots[middle]
                low = middle + 1
            } else high = middle - 1
        }
        return found
    }

    fun leafRef(root: Root, key: TileKey): Long {
        val ux = unsigned(key.x)
        val uz = unsigned(key.z)
        if (ux ushr root.level != root.x || uz ushr root.level != root.z) return 0
        var ref = root.node
        for (level in root.level downTo 1) {
            if (ref == 0L) return 0
            nodesVisited.incrementAndGet()
            ref = node(ref).children[quarter(key, level)]
        }
        return ref
    }

    /** Leaf refs of a [side]×[side] window at [epoch], row-major, sharing one traversal. */
    fun window(epoch: Long, x0: Int, z0: Int, side: Int): LongArray {
        val out = LongArray(side * side)
        val root = rootAt(epoch) ?: return out
        val ux0 = unsigned(x0)
        val uz0 = unsigned(z0)
        fun visit(level: Int, x: Int, z: Int, ref: Long) {
            if (ref == 0L) return
            val size = 1 shl level
            val left = x shl level
            val top = z shl level
            if (left >= ux0 + side || left + size <= ux0 || top >= uz0 + side || top + size <= uz0) return
            if (level == 0) {
                out[(top - uz0) * side + (left - ux0)] = ref
                return
            }
            nodesVisited.incrementAndGet()
            val node = node(ref)
            for (quarter in 0 until 4)
                visit(level - 1, (x shl 1) or (quarter and 1), (z shl 1) or (quarter shr 1), node.children[quarter])
        }
        visit(root.level, root.x, root.z, root.node)
        return out
    }

    /** One surface sample per [level]-square (2^level chunks per side) of a window, from nodes alone. */
    fun samples(epoch: Long, level: Int, x0: Int, z0: Int, side: Int): LongArray {
        require(level >= 1)
        val out = LongArray(side * side) { Sample.NONE.packed }
        val root = rootAt(epoch) ?: return out
        val sx0 = unsigned(x0) ushr level
        val sz0 = unsigned(z0) ushr level
        fun visit(at: Int, x: Int, z: Int, ref: Long) {
            if (ref == 0L) return
            val shift = at - level
            if ((x + 1 shl shift) <= sx0 || (x shl shift) >= sx0 + side || (z + 1 shl shift) <= sz0 || (z shl shift) >= sz0 + side) return
            nodesVisited.incrementAndGet()
            val node = node(ref)
            for (quarter in 0 until 4) {
                val childX = (x shl 1) or (quarter and 1)
                val childZ = (z shl 1) or (quarter shr 1)
                if (at - 1 == level) {
                    val px = childX - sx0
                    val pz = childZ - sz0
                    if (node.children[quarter] != 0L && px in 0 until side && pz in 0 until side)
                        out[pz * side + px] = node.samples[quarter]
                } else visit(at - 1, childX, childZ, node.children[quarter])
            }
        }
        if (root.level > level) visit(root.level, root.x, root.z, root.node)
        return out
    }

    fun summary(leafRef: Long): TileRecord = leaf(leafRef).let { summaries.read(it.summary).withEpoch(it.epoch) }

    fun volume(leafRef: Long): ChunkVolume {
        val leaf = leaf(leafRef)
        val biomes = summaries.read(leaf.summary).channel(TileRecord.Channel.BIOME)
        return ChunkVolume(Array(ChunkLeaf.SECTIONS) { leaf.section(it).takeIf { ref -> ref != 0L }?.let(sections::read) }, biomes)
    }

    /** Chunks that differ between two moments, by descending only where the two trees stop sharing. */
    fun diff(from: Long, to: Long): List<Change> {
        val changes = ArrayList<Change>()
        val a = rootAt(from)
        val b = rootAt(to) ?: return changes
        val synthetic = HashMap<Long, TreeNode>()
        var aRef = a?.node ?: 0L
        if (a != null) {
            var level = a.level
            var x = a.x
            var z = a.z
            while (level < b.level) {
                val quarter = (x and 1) or ((z and 1) shl 1)
                val lifted = TreeNode(LongArray(4), LongArray(4) { Sample.NONE.packed }, 0)
                lifted.children[quarter] = aRef
                val id = -(synthetic.size + 1L)
                synthetic[id] = lifted
                aRef = id
                level++
                x = x ushr 1
                z = z ushr 1
            }
            check(x == b.x && z == b.z) { "A later root always covers an earlier one" }
        }
        fun resolve(ref: Long): TreeNode = when {
            ref == 0L -> TreeNode.EMPTY
            ref < 0L -> synthetic.getValue(ref)
            else -> node(ref)
        }
        fun visit(level: Int, x: Int, z: Int, left: Long, right: Long) {
            if (left == right) return
            if (level == 0) {
                val before = left.takeIf { it != 0L }?.let(::leaf)
                val after = right.takeIf { it != 0L }?.let(::leaf)
                var mask = 0
                for (slot in 0 until ChunkLeaf.SLOTS)
                    if ((before?.slots?.get(slot) ?: 0L) != (after?.slots?.get(slot) ?: 0L)) mask = mask or (1 shl slot)
                if (mask != 0) changes += Change(TileKey(x - OFFSET, z - OFFSET), mask)
                return
            }
            nodesVisited.incrementAndGet()
            val l = resolve(left)
            val r = resolve(right)
            for (quarter in 0 until 4)
                visit(level - 1, (x shl 1) or (quarter and 1), (z shl 1) or (quarter shr 1), l.children[quarter], r.children[quarter])
        }
        visit(b.level, b.x, b.z, aRef, b.node)
        return changes
    }

    fun clearCaches() {
        sectionCache.clear()
        leaves.clear()
        nodes.clear()
    }

    private fun leaf(ref: Long): ChunkLeaf = leaves.get(ref) { ChunkLeaf.decode(pack.read(ref), Pack.offset(ref), ::leaf) }

    private fun node(ref: Long): TreeNode = nodes.get(ref) { TreeNode.decode(pack.read(ref), Pack.offset(ref), ::node) }

    override fun close() = pack.close()

    private companion object {
        const val OFFSET = 1 shl 20

        fun unsigned(coordinate: Int): Int = coordinate + OFFSET

        fun quarter(key: TileKey, level: Int): Int =
            ((unsigned(key.x) ushr (level - 1)) and 1) or (((unsigned(key.z) ushr (level - 1)) and 1) shl 1)

        fun contentHash(values: IntArray): Long {
            var hash = 0x2545F4914F6CDD1DL
            for (value in values) {
                hash = (hash xor value.toLong()) * -0x40a7b892e31b1a47L
                hash = hash xor (hash ushr 29)
            }
            return hash
        }
    }
}
