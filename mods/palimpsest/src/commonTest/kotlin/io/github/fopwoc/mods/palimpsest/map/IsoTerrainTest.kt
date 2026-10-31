package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class IsoTerrainTest {
    /** Flat grey ground everywhere; a read waits while [gate] is closed. */
    private class Ground : LiveTiles {
        @Volatile var gate = CountDownLatch(0)
        @Volatile var reads = 0

        override val shader = TerrainShader({ 0x808080 }, { 0 }, { 0xFFFFFF })

        override fun tiles(x0: Int, z0: Int, width: Int, height: Int): Array<TileRecord?> {
            gate.await(WAIT_SECONDS, TimeUnit.SECONDS)
            reads++
            return Array(width * height) { TileRecord.solid(0, 1, height = 64, biome = 1) }
        }

        override fun addChangeListener(listener: (Collection<TileKey>) -> Unit) = Unit

        override fun removeChangeListener(listener: (Collection<TileKey>) -> Unit) = Unit
    }

    private fun camera(pixelsPerBlock: Double) =
        IsoCamera(0.0, 64.0, 0.0, pixelsPerBlock, 0.0, IsoCamera.MAX_PITCH, 200, 200)

    private fun IsoTerrain.settle(camera: IsoCamera, until: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!until()) {
            if (System.nanoTime() > deadline) fail("Terrain did not settle")
            request(camera, MapTime.Live, null)
            Thread.sleep(1)
        }
    }

    @Test
    fun aChangeOfDetailKeepsTheShownGroundUntilTheNewOneLands() {
        val ground = Ground()
        IsoTerrain(null, ground).use { terrain ->
            val near = camera(2.0)
            terrain.settle(near) { terrain.meshes.isNotEmpty() }
            val shown = terrain.meshes

            // Zoomed out, the view wants coarser cells over more ground; hold that read.
            ground.gate = CountDownLatch(1)
            val readsBefore = ground.reads
            val far = camera(0.125)
            repeat(FRAMES) {
                terrain.request(far, MapTime.Live, null)
                assertEquals(
                    shown,
                    terrain.meshes,
                    "the old ground stays while the new one is read",
                )
                Thread.sleep(1)
            }
            ground.gate.countDown()
            terrain.settle(far) { ground.reads > readsBefore && terrain.meshes != shown }
            assertTrue(terrain.meshes.isNotEmpty())
            assertEquals(64 + 1, terrain.topAt(300, -300))
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
        const val FRAMES = 20
    }
}
