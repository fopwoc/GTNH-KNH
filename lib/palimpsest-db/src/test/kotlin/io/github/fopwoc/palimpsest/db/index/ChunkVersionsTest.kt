package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.codec.ContentHash
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkVersionsTest {
    @Test
    fun `every version reads back across keyframes and shape changes`() {
        val random = Random(11)
        val chunk = ChunkVersions()
        val originals = ArrayList<LongArray>()
        var slots = 17
        var current = Versions.empty(0, 0, slots)
        repeat(100) { index ->
            if (index == 50) {
                slots = 25
                current = Versions.empty(0, -4, slots)
            }
            val version = current.copyOf()
            version[0] = index * 10L + 5
            repeat(random.nextInt(1, 4)) {
                val slot = random.nextInt(slots)
                Versions.set(
                    version,
                    slot,
                    random.nextLong(1, 1L shl 40),
                    random.nextInt(1, 999),
                    ContentHash(random.nextLong(), random.nextLong()),
                )
            }
            chunk.add(version, ByteArray(1) { index.toByte() })
            originals += version
            current = version
        }
        originals.forEachIndexed { index, version ->
            assertContentEquals(version, chunk.version(index), "version $index")
            assertEquals(index.toByte(), chunk.surface(index)[0])
        }
        assertEquals(42, chunk.search(429))
        assertEquals(null, chunk.search(4))
        assertTrue(chunk.changedBetween(14, 15))
        assertFalse(chunk.changedBetween(15, 24))
        assertTrue(chunk.changedBetween(-1, 5))
        assertFalse(chunk.changedBetween(995, 2000))
    }
}
