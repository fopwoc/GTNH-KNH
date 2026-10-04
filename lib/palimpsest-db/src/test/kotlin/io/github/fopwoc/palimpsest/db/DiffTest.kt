package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffTest {
    private val home = ChunkPos(0, 0)
    private val mine = ChunkPos(50, 50)

    private fun PalimpsestDb.fill() {
        val stone = vocabulary.id("minecraft:stone:0")
        val grass = vocabulary.id("minecraft:grass:0")
        val torch = vocabulary.id("minecraft:torch:5")
        val air = BlockId.AIR
        val dimension = dimension(OVERWORLD)
        dimension
            .commit(WorldTick(10), listOf(chunk(home, stone, grass), chunk(mine, stone, grass)))
            .await()
        dimension
            .commit(
                WorldTick(20),
                listOf(chunk(home, stone, grass, extra = Triple(3, 65, 3) to torch)),
            )
            .await()
        dimension
            .commit(
                WorldTick(30),
                listOf(chunk(mine, stone, grass, extra = Triple(5, 12, 5) to air)),
            )
            .await()
    }

    private fun PalimpsestDb.check() {
        val dimension = dimension(OVERWORLD)
        fun diff(from: Long, to: Long, window: ChunkWindow? = null) =
            dimension
                .diff(WorldTick(from), WorldTick(to), window)
                .result
                .await()
                .changes
                .associateBy { it.pos }

        val first = diff(0, 10)
        assertEquals(setOf(home, mine), first.keys)
        assertTrue(first.getValue(home).appeared)

        val built = diff(10, 20).values.single()
        assertEquals(home, built.pos)
        assertEquals(1L shl 4, built.sections)
        assertTrue(built.surface)
        assertFalse(built.appeared)

        val dug = diff(20, 30).values.single()
        assertEquals(1L shl 0, dug.sections)
        assertFalse(dug.surface)

        assertEquals(setOf(home, mine), diff(10, 30).keys)
        assertEquals(setOf(mine), diff(10, 30, ChunkWindow(40, 40, 20, 20)).keys)
        assertEquals(emptySet(), diff(30, 1000).keys)
        assertEquals(emptySet(), diff(11, 19).keys)
    }

    @Test
    fun `diffs find what changed between any two moments`() {
        TestWorld().use { world ->
            world.open().use {
                it.fill()
                it.check()
            }
            world.open().use { it.check() }
            world.config.cacheDirectory.toFile().deleteRecursively()
            world.open().use { it.check() }
        }
    }
}
