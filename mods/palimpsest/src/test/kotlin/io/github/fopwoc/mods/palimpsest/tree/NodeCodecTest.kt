package io.github.fopwoc.mods.palimpsest.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeCodecTest {
    @Test
    fun roundTripsAndStaysAroundFiftyBytes() {
        var node = NodeRecord.EMPTY
        for (quarter in 0 until 4) {
            node = node.with(quarter, Ref(3, 1_000_000 + quarter * 500), Sample(40 + quarter, 64, 0, 7), 1_700_000_000_000L + quarter)
        }
        val bytes = ByteSink().also { NodeCodec.encode(it, node) }.toByteArray()
        assertTrue(bytes.size <= 52, "full node: ${bytes.size}")
        assertEquals(node, NodeCodec.decode(ByteSource(bytes)))
        assertEquals(Sample(40, 64, 0, 7), node.sample)
        assertEquals(1_700_000_000_003L, node.maxEpoch)
    }

    @Test
    fun sparseNodeSkipsAbsentQuarters() {
        val node = NodeRecord.EMPTY.with(2, Ref(0, 5), Sample(1, 2, 3, 4), 10)
        val bytes = ByteSink().also { NodeCodec.encode(it, node) }.toByteArray()
        assertTrue(bytes.size <= 1 + 3 + 2 + 6, "sparse node: ${bytes.size}")
        val decoded = NodeCodec.decode(ByteSource(bytes))
        assertEquals(node, decoded)
        assertEquals(Sample(1, 2, 3, 4), decoded.sample)
        assertTrue(decoded.child(0).isNull && decoded.sample(0).isNone)
    }

    @Test
    fun quarterMapsCoordinatesByLevelBit() {
        assertEquals(0, NodeRecord.quarter(0, 0, 1))
        assertEquals(1, NodeRecord.quarter(1, 0, 1))
        assertEquals(2, NodeRecord.quarter(0, 1, 1))
        assertEquals(3, NodeRecord.quarter(3, 3, 2))
        assertEquals(1, NodeRecord.quarter(2, 1, 2))
    }
}
