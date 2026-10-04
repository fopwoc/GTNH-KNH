package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * What the 3D tree is for: checking out any moment and moving through time. Checkout reads a
 * window's leaves; top-down needs only their 2D summaries, other ceilings decode sections. Diff
 * descends only where two moments' trees differ. Playback steps one commit at a time and touches
 * only what the step changed. Every answer is checked against the flat store and the 2.4 tree.
 */
class TreeReads(
    private val stores: PairedStores,
    private val vocabulary: Vocabulary,
    private val minX: Int,
    private val minZ: Int,
    private val maxX: Int,
    private val maxZ: Int,
    private val epochs: LongRange,
    private val side: Int = 32,
    private val samples: Int = 48,
    seed: Long = 11,
) {
    class Line(val label: String, val text: String)

    private val random = Random(seed)
    private val tree = stores.tree3d
    private val area = (minX..maxX).flatMap { x -> (minZ..maxZ).map { z -> TileKey(x, z) } }

    private fun epoch() = random.nextLong(epochs.first, epochs.last + 1)

    private fun corner() = random.nextInt(minX, maxX - side + 2) to random.nextInt(minZ, maxZ - side + 2)

    fun run(): List<Line> {
        val lines = mutableListOf<Line>()
        lines += verify()
        lines += checkout()
        lines += diffs()
        lines += playback()
        return lines
    }

    private fun verify(): Line {
        var volumes = 0
        var surfaces = 0
        repeat(400) {
            val key = area.random(random)
            val epoch = epoch()
            val ref = tree.rootAt(epoch)?.let { tree.leafRef(it, key) } ?: 0L
            val flat = stores.volumes.volume(key, epoch)
            val tile = stores.tree.tile(key, epoch)
            if (ref == 0L) {
                if (flat != null) volumes++
                if (tile != null) surfaces++
                return@repeat
            }
            val mine = tree.volume(ref)
            if (flat == null || (0 until 16).any { !(mine.section(it) contentEquals flat.section(it)) } ||
                !(mine.biomes() contentEquals flat.biomes())) volumes++
            if (tile == null || !tile.sameFacts(tree.summary(ref))) surfaces++
        }
        return Line("verification, 400 random chunk moments", "$volumes volume and $surfaces surface mismatches")
    }

    private fun checkout(): List<Line> {
        val kinds = vocabulary.kinds()
        val requests = List(samples) { epoch() to corner() }
        val lines = mutableListOf<Line>()
        Executors.newFixedThreadPool(4).use { pool ->
            fun measure(label: String, cold: Boolean, threads: Int, view: (LongArray, Int, Long) -> Unit) {
                val times = mutableListOf<Long>()
                val visited = tree.nodesVisited.get()
                if (!cold) requests.forEach { (epoch, corner) ->
                    val refs = tree.window(epoch, corner.first, corner.second, side)
                    for (at in refs.indices) view(refs, at, epoch)
                }
                for ((epoch, corner) in requests) {
                    if (cold) tree.clearCaches()
                    timed(times) {
                        val refs = tree.window(epoch, corner.first, corner.second, side)
                        val stripe = (refs.size + threads - 1) / threads
                        val tasks = (0 until threads).map { part ->
                            Callable { for (at in part * stripe until minOf(refs.size, (part + 1) * stripe)) view(refs, at, epoch) }
                        }
                        if (threads == 1) tasks.single().call() else pool.invokeAll(tasks).forEach { it.get() }
                    }
                }
                val nodes = (tree.nodesVisited.get() - visited) / (requests.size * if (cold) 1 else 2)
                lines += Line(label, "${Timings(times)}  (~$nodes nodes/view)")
            }
            val topDown: (LongArray, Int, Long) -> Unit = { refs, at, _ -> if (refs[at] != 0L) tree.summary(refs[at]) }
            val cave: (LongArray, Int, Long) -> Unit = { refs, at, epoch ->
                if (refs[at] != 0L) surface(tree.volume(refs[at]), kinds, epoch, 40)
            }
            measure("tree checkout, top-down from leaf summaries, cleared caches", true, 1, topDown)
            measure("tree checkout, top-down from leaf summaries, warm", false, 1, topDown)
            measure("tree checkout, view from y=40, 4 threads, cleared caches", true, 4, cave)
            measure("tree checkout, view from y=40, 4 threads, warm", false, 4, cave)
        }
        return lines
    }

    private fun diffs(): List<Line> {
        val treeTimes = mutableListOf<Long>()
        val bruteTimes = mutableListOf<Long>()
        var wrong = 0
        var changed = 0L
        val visited = tree.nodesVisited.get()
        repeat(samples) {
            val a = epoch()
            val b = epoch()
            val (from, to) = minOf(a, b) to maxOf(a, b)
            val diff = timed(treeTimes) { tree.diff(from, to) }
            val brute = timed(bruteTimes) {
                val ra = tree.rootAt(from)
                val rb = tree.rootAt(to)!!
                area.filter { (ra?.let { r -> tree.leafRef(r, it) } ?: 0L) != tree.leafRef(rb, it) }.toSet()
            }
            changed += diff.size
            if (diff.map { it.key }.toSet() != brute) wrong++
        }
        val nodes = (tree.nodesVisited.get() - visited)
        return listOf(
            Line("diff of two random moments, whole area (tree)", "${Timings(treeTimes)}  ~${changed / samples} changed chunks"),
            Line("same answer by looking up every chunk", "${Timings(bruteTimes)}  ($wrong disagreements, ${nodes / samples} nodes/pair both ways)"),
        )
    }

    private fun playback(): List<Line> {
        val kinds = vocabulary.kinds()
        val steps = mutableListOf<Long>()
        val caveSteps = mutableListOf<Long>()
        var touched = 0L
        repeat(4) {
            val start = random.nextLong(epochs.first, epochs.last - 200)
            for (epoch in start until start + 200) {
                val changes = timed(steps) {
                    tree.diff(epoch, epoch + 1).onEach { change ->
                        tree.rootAt(epoch + 1)?.let { root -> tree.summary(tree.leafRef(root, change.key)) }
                    }
                }
                timed(caveSteps) {
                    for (change in changes) {
                        if (change.slots and 0xFFFF == 0) continue
                        val root = tree.rootAt(epoch + 1) ?: continue
                        surface(tree.volume(tree.leafRef(root, change.key)), kinds, epoch + 1, 40)
                    }
                }
                touched += changes.size
            }
        }
        return listOf(
            Line("playback step: diff + new top-down summaries", "${Timings(steps)}  (~%.1f chunks/step)".format(touched / 800.0)),
            Line("playback step: + rescan changed chunks from y=40", Timings(caveSteps).toString()),
        )
    }
}
