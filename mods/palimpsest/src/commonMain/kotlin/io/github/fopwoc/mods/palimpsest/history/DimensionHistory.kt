package io.github.fopwoc.mods.palimpsest.history

import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.render.iso.VoxelChunk
import io.github.fopwoc.mods.palimpsest.render.iso.VoxelKind
import io.github.fopwoc.mods.palimpsest.render.iso.VoxelPalette
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkVolume
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.WorldTick
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * The history of the dimension the client is in. The scanner stages 3D snapshots of loaded chunks
 * here; every commit interval they become one moment of history. Block ids of the database are
 * drawn with the colours of the map's [blocks] table, matched by identity.
 */
class DimensionHistory(
    private val world: WorldHistory,
    id: DimensionId,
    private val blocks: BlockTable,
    classify: (String) -> BlockClass?,
    private val interval: () -> Duration,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val dimension: Dimension = world.db.dimension(id)

    /** History block id → [blocks] id; grown on the game thread, read by the map's readers. */
    @Volatile private var drawn = IntArray(0)
    /** History block id → how the voxel view draws it, grown alongside [drawn]. */
    @Volatile private var voxelKinds = ByteArray(0)
    /** What the scan staged since the last commit that has it, for the live voxel view. */
    private val recent = ConcurrentHashMap<ChunkPos, ChunkObservation>()
    private var lastCommit = clock()

    init {
        // Blocks of earlier sessions this table has not seen yet get guessed colours from their
        // static look, until the live scan meets them.
        for (raw in 1 until world.db.vocabulary.size) {
            val identity = world.db.vocabulary.identity(BlockId(raw))
            remember(
                raw,
                blocks.idOf(identity).takeIf { it != 0 }
                    ?: classify(identity)?.let { blocks.guess(identity, it.color, it.tint) }
                    ?: 0,
            )
        }
    }

    /** The [blocks] id to draw history block [raw] with; 0 when it has no colour. */
    fun drawnId(raw: Int): Int = drawn.let { if (raw in it.indices) it[raw] else 0 }

    /**
     * The history id of a block met at a scan, recording its kind the first time; game thread.
     * Callers cache it per game block. [look] may come without a world position, so its colour is
     * only a guess; the map's own scan records the real one.
     */
    fun idOf(identity: String, look: BlockClass): BlockId {
        val id = world.db.vocabulary.id(identity)
        world.learn(identity, look.kind)
        if (drawnId(id.raw) == 0) remember(id.raw, blocks.guess(identity, look.color, look.tint))
        return id
    }

    fun stage(observation: ChunkObservation) {
        recent[observation.pos] = observation
        dimension.stage(observation)
    }

    /** How the voxel view draws history blocks, coloured by [shader]; a snapshot of this moment. */
    fun palette(shader: TerrainShader): VoxelPalette = VoxelPalette(voxelKinds, drawn, shader)

    /**
     * Every block of chunk [pos] at [tick], or live, with what the scan staged since the last
     * commit, for [Long.MAX_VALUE]; null where history never saw it.
     */
    fun voxels(pos: ChunkPos, tick: Long): CompletableFuture<VoxelChunk?> {
        if (tick == Long.MAX_VALUE)
            recent[pos]?.let {
                return CompletableFuture.completedFuture(it.voxels())
            }
        return dimension.at(WorldTick(tick)).volume(pos).result.thenApply { it?.voxels() }
    }

    /**
     * Every second or so on the game thread: commits what was staged once an interval has passed.
     */
    fun tick(worldTime: Long) {
        if (clock() - lastCommit >= interval().toMillis()) commitNow(worldTime)
    }

    /**
     * Commits whatever is staged now, for leaving the dimension or an explicit save; null when
     * nothing is staged.
     */
    fun commitNow(worldTime: Long): CompletableFuture<Commit>? {
        lastCommit = clock()
        if (dimension.stagedCount == 0) return null
        val committed = HashMap(recent)
        return dimension.commitStaged(WorldTick(worldTime)).whenComplete { _, failure ->
            // History has these looks now; a newer stage since the commit stays.
            if (failure == null)
                committed.forEach { (pos, observation) -> recent.remove(pos, observation) }
        }
    }

    private fun remember(raw: Int, drawnId: Int) {
        val current = drawn
        val size = if (raw < current.size) current.size else maxOf(raw + 1, current.size * 2)
        drawn = current.copyOf(size).also { it[raw] = drawnId }
        val identity = world.db.vocabulary.identity(BlockId(raw))
        val kind = world.kind(identity).voxel
        val look = kind.ordinal or if (isShiny(identity)) VoxelPalette.SHINY else 0
        voxelKinds = voxelKinds.copyOf(size).also { it[raw] = look.toByte() }
    }

    /** Polished blocks that mirror the light, by name: metal and gem blocks, ice, casings. */
    private fun isShiny(identity: String): Boolean {
        val name = identity.substringBefore('@').lowercase()
        return SHINY_NAMES.any { it in name }
    }

    private val BlockKind.voxel: VoxelKind
        get() =
            when (this) {
                BlockKind.AIR,
                BlockKind.TRANSPARENT -> VoxelKind.EMPTY
                BlockKind.WATER -> VoxelKind.WATER
                BlockKind.DECORATION -> VoxelKind.DECAL
                BlockKind.LIQUID,
                BlockKind.SOLID -> VoxelKind.SOLID
            }

    private fun ChunkVolume.voxels(): VoxelChunk {
        val height = maxY - minY
        val blocks = IntArray(SIDE * SIDE * height)
        for (y in 0 until height) for (z in 0 until SIDE) for (x in 0 until SIDE) blocks[
            (y * SIDE + z) * SIDE + x] = block(x, minY + y, z).raw
        return VoxelChunk(pos.x, pos.z, minY, height, blocks, biomes.columns())
    }

    private fun ChunkObservation.voxels(): VoxelChunk {
        val blocks = IntArray(SECTION_VOLUME * sections.size)
        // Sections and chunks alike run YZX, so each section is the next stretch of the chunk.
        sections.forEachIndexed { index, section ->
            section?.unpack()?.copyInto(blocks, index * SECTION_VOLUME)
        }
        return VoxelChunk(
            pos.x,
            pos.z,
            minSection * SIDE,
            sections.size * SIDE,
            blocks,
            biomes.columns(),
        )
    }

    private fun Biomes.columns(): IntArray = (this as Biomes.Columns).values.copyOf()

    private companion object {
        const val SIDE = 16
        const val SECTION_VOLUME = SIDE * SIDE * SIDE
        val SHINY_NAMES =
            listOf(
                "iron_block",
                "gold_block",
                "diamond_block",
                "emerald_block",
                "copper_block",
                "netherite_block",
                "quartz_block",
                "ice",
                "gt.blockmetal",
                "blockcasings",
                "blockmetal",
            )
    }
}
