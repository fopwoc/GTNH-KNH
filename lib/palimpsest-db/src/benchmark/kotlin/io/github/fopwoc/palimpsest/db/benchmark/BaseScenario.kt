package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.SectionBlocks
import kotlin.random.Random

/** A player at home: the same window every commit, with small edits of one [Activity]. */
class BaseScenario(
    vocabulary: BlockVocabulary,
    private val activity: Activity,
    override val commits: Int = 300,
) : Scenario {
    enum class Activity {
        /** 30 blocks placed in each of 3 chunks. */
        BUILD,

        /** One leaf toggles its decay bit in 10% of chunks: what un-normalized metadata does. */
        DECAY,
    }

    private val world = SyntheticWorld(SEED, vocabulary)
    private val random = Random(SEED)
    private val positions = window(ChunkPos(0, 0), RADIUS)
    private val sections = HashMap<ChunkPos, Array<IntArray?>>()
    private val chunks = HashMap<ChunkPos, ChunkObservation>()
    override val seen = positions.toSet()

    init {
        positions
            .parallelMap { it to world.sections(it) }
            .forEach { (pos, blocks) ->
                sections[pos] = blocks
                chunks[pos] = world.observe(pos, blocks)
            }
    }

    override fun observe(index: Int): List<ChunkObservation> {
        if (index > 0) {
            val edited =
                when (activity) {
                    Activity.BUILD -> positions.shuffled(random).take(3).onEach(::build)
                    Activity.DECAY ->
                        positions.shuffled(random).take(positions.size / 10).filter(::decay)
                }
            edited.forEach { chunks[it] = world.observe(it, sections.getValue(it)) }
        }
        return positions.map(chunks::getValue)
    }

    private fun build(pos: ChunkPos) {
        val blocks = sections.getValue(pos)
        val x = random.nextInt(16)
        val z = random.nextInt(16)
        val base = world.height(pos.x * 16 + x, pos.z * 16 + z) + 1
        val block = listOf(world.planks, world.cobblestone, world.glass).random(random)
        repeat(30) { step ->
            val y = (base + step / 3).coerceAtMost(SyntheticWorld.HEIGHT - 1)
            val section =
                blocks[y shr 4] ?: IntArray(SectionBlocks.VOLUME).also { blocks[y shr 4] = it }
            section[SectionBlocks.index((x + step % 3) and 15, y and 15, z)] = block
        }
    }

    /** Flips one leaf in [pos]; false when the chunk has no trees. */
    private fun decay(pos: ChunkPos): Boolean {
        val leaves =
            sections.getValue(pos).withIndex().flatMap { (index, section) ->
                section
                    ?.indices
                    ?.filter { section[it] == world.leaves || section[it] == world.decayingLeaves }
                    ?.map { index to it }
                    .orEmpty()
            }
        if (leaves.isEmpty()) return false
        val (index, at) = leaves.random(random)
        val section = sections.getValue(pos)[index]!!
        section[at] = if (section[at] == world.leaves) world.decayingLeaves else world.leaves
        return true
    }

    private companion object {
        const val SEED = 7L
        const val RADIUS = 8
    }
}
