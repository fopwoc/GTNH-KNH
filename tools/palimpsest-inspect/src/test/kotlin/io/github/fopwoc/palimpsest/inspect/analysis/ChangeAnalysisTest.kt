package io.github.fopwoc.palimpsest.inspect.analysis

import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.inspect.TestHistory
import io.github.fopwoc.palimpsest.inspect.history.HistoryCopy
import io.github.fopwoc.palimpsest.inspect.history.StderrLog
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeAnalysisTest {
    private val base = ChunkPos(0, 0)
    private val workshop = ChunkPos(1, 0)

    @Test
    fun sortsLossesThatComeBackMachineDetailsAndRealChanges() {
        TestHistory().use { test ->
            test.write {
                val stone = id("minecraft:stone:0")
                val grass = id("minecraft:grass:0")
                val cable = id("appliedenergistics2:tile.BlockCableBus:0")
                val machine = id("gregtech:gt.blockmachines@mte1/c-1")
                val painted = id("gregtech:gt.blockmachines@mte1/c0")
                val built: (Int, Int, Int) -> BlockId = { _, y, _ ->
                    when {
                        y < 16 -> stone
                        y < 18 -> cable
                        else -> BlockId.AIR
                    }
                }
                val floor: (Int, Int, Int) -> BlockId = { _, y, _ ->
                    if (y < 16) stone else BlockId.AIR
                }
                fun withMachine(block: BlockId): (Int, Int, Int) -> BlockId = { x, y, z ->
                    if (x == 3 && y == 16 && z == 3) block else floor(x, y, z)
                }
                commit(10, base to built, workshop to floor)
                // The cables vanish over two commits, then come back exactly.
                commit(20, base to { x, y, z -> if (y == 17) BlockId.AIR else built(x, y, z) })
                commit(30, base to floor)
                commit(40, base to built)
                commit(50, workshop to withMachine(machine))
                commit(60, workshop to withMachine(painted))
                commit(70, base to { x, y, z -> if (y == 15) grass else built(x, y, z) })
            }
            HistoryCopy.open(test.history, null, StderrLog(LogLevel.ERROR)).use { copy ->
                val summary =
                    ChangeAnalysis(copy.db.dimension(TestHistory.OVERWORLD), copy.db.vocabulary)
                        .run()
                assertEquals(7, summary.commits)
                assertEquals(2, summary.appeared)
                assertEquals(0, summary.identical)

                val undone = summary.undoneLosses.single()
                assertEquals(base, undone.pos)
                assertEquals(512, undone.blocks)
                assertEquals(20, undone.lostAt)
                assertEquals(40, undone.backAt)
                assertEquals(512L, summary.lostBlocks)
                assertEquals(1, summary.sectionsEmptied)
                // The cables' section coming back, and the machine starting one of its own.
                assertEquals(2, summary.sectionsFilled)

                assertEquals(
                    mapOf(
                        ("gregtech:gt.blockmachines@mte1/c-1" to
                            "gregtech:gt.blockmachines@mte1/c0") to 1
                    ),
                    summary.sameBlockPairs,
                )
                assertEquals(256, summary.blockPairs["minecraft:stone:0" to "minecraft:grass:0"])
            }
        }
    }
}
