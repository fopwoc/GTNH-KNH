package io.github.fopwoc.mods.palimpsest.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeCodecTest {
    private val refs = RefCoder.Direct

    private fun fullNode(): NodeRecord {
        var node = NodeRecord.EMPTY
        for (quarter in 0 until 4) {
            node = node.with(quarter, Ref(3, 1_000_000 + quarter * 500), Sample(40 + quarter, 64, 0, 7), 1_700_000_000_000L + quarter)
        }
        return node
    }

    @Test
    fun fullNodeRoundTripsAndStaysUnderFiftyBytes() {
        val node = fullNode()
        val bytes = ByteSink().also { NodeCodec.encodeFull(it, node, refs) }.toByteArray()
        assertTrue(bytes.size <= 52, "full node: ${bytes.size}")
        val decoded = NodeCodec.decode(ByteSource(bytes), refs)
        assertTrue(decoded.isFull)
        assertEquals(node, decoded.node)
        assertEquals(Sample(40, 64, 0, 7), node.sample)
        assertEquals(1_700_000_000_003L, node.maxEpoch)
    }

    @Test
    fun sparseNodeSkipsAbsentQuarters() {
        val node = NodeRecord.EMPTY.with(2, Ref(0, 5), Sample(1, 2, 3, 4), 10)
        val bytes = ByteSink().also { NodeCodec.encodeFull(it, node, refs) }.toByteArray()
        assertTrue(bytes.size <= 1 + 1 + 3 + 2 + 6, "sparse node: ${bytes.size}")
        val decoded = checkNotNull(NodeCodec.decode(ByteSource(bytes), refs).node)
        assertEquals(node, decoded)
        assertEquals(2, decoded.onlyQuarter)
        assertTrue(decoded.child(0).isNull && decoded.sample(0).isNone)
    }

    @Test
    fun patchCarriesOnlyTheChangedQuarterAndAppliesOnItsBase() {
        val base = fullNode()
        val changed = base.with(2, Ref(3, 2_000_000), Sample(99, 70, 0, 7), 1_700_000_000_010L)
        val sink = ByteSink()
        assertTrue(NodeCodec.encodePatch(sink, changed, base, Ref(3, 500), refs))
        val bytes = sink.toByteArray()
        assertTrue(bytes.size <= 1 + 7 + 3 + 1 + 1 + 4 + 6, "patch: ${bytes.size}")
        val decoded = NodeCodec.decode(ByteSource(bytes), refs)
        assertFalse(decoded.isFull)
        assertNull(decoded.node)
        assertEquals(Ref(3, 500), decoded.base)
        val applied = decoded.apply(base)
        assertEquals(changed, applied)
        assertEquals(1, applied.patchDepth)
    }

    @Test
    fun patchRefusesDeepChainsAndWideChanges() {
        val base = fullNode().withPatchDepth(NodeCodec.MAX_PATCH_DEPTH)
        val changed = base.with(0, Ref(1, 1), Sample(1, 1, 1, 1), 5)
        assertFalse(NodeCodec.encodePatch(ByteSink(), changed, base, Ref(0, 0), refs))
        var wide = fullNode()
        for (quarter in 0 until 3) wide = wide.with(quarter, Ref(9, quarter), Sample(9, 9, 9, 9), 5)
        assertFalse(NodeCodec.encodePatch(ByteSink(), wide, fullNode(), Ref(0, 0), refs))
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
