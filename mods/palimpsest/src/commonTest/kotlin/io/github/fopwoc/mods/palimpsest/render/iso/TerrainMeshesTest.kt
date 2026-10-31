package io.github.fopwoc.mods.palimpsest.render.iso

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TerrainMeshesTest {
    /** 128 × 32 columns from [originX], four regions of ground that varies with the world x. */
    private fun field(originX: Int = 0, bumpX: Int? = null): Heightfield {
        val columns = 128
        val tops = IntArray(columns * 32) { 64 + Math.floorMod(originX + it % columns, 5) }
        if (bumpX != null) tops[bumpX - originX] += 3
        return Heightfield(
            originX,
            0,
            1,
            columns,
            32,
            tops,
            IntArray(tops.size) { 0xFF00FF00.toInt() },
        )
    }

    @Test
    fun aChangedColumnRebuildsOnlyTheRegionsWithinReach() {
        val meshes = TerrainMeshes()
        val before = meshes.meshes(field()).associateBy { it.originX }
        val after = meshes.meshes(field(bumpX = 60)).associateBy { it.originX }
        assertEquals(4, after.size)
        assertSame(before[0.0], after[0.0])
        assertNotSame(before[32.0], after[32.0])
        assertSame(before[96.0], after[96.0])
    }

    @Test
    fun aPanKeepsRegionsWhoseSurroundingsAreTheSame() {
        val meshes = TerrainMeshes()
        val before = meshes.meshes(field()).associateBy { it.originX }
        val after = meshes.meshes(field(originX = 32)).associateBy { it.originX }
        assertEquals(setOf(32.0, 64.0, 96.0, 128.0), after.keys)
        // Only the old edges changed what lies around them.
        assertSame(before[64.0], after[64.0])
        assertNotSame(before[32.0], after[32.0])
        assertNotSame(before[96.0], after[96.0])
    }
}
