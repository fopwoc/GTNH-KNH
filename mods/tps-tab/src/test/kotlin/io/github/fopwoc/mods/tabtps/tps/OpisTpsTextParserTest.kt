package io.github.fopwoc.mods.tabtps.tps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpisTpsTextParserTest {
    @Test
    fun parsesOverallAndCurrentDimensionFromOpisLines() {
        val descriptor = DimensionDescriptor(
            id = 0,
            displayName = "Overworld",
            aliases = setOf("overworld", "surface", "world")
        )

        val report = OpisTpsTextParser.parse(
            listOf(
                "Server TPS: 19.82 · MSPT: 50.46",
                "Overworld: 17.25 TPS · 57.97 ms/t"
            ),
            descriptor
        )

        assertNotNull(report.overall)
        assertNotNull(report.currentDimension)
        assertEquals(19.82, report.overall!!.tps, 0.0001)
        assertEquals(50.46, report.overall!!.mspt, 0.0001)
        assertEquals(17.25, report.currentDimension!!.tps, 0.0001)
        assertEquals(57.97, report.currentDimension!!.mspt, 0.0001)
    }

    @Test
    fun parsesGenericUnlabeledTpsLineAsOverall() {
        val report = OpisTpsTextParser.parse(listOf("19.82 TPS · 50.46 ms/t"), null)

        assertNotNull(report.overall)
        assertNull(report.currentDimension)
        assertEquals(19.82, report.overall!!.tps, 0.0001)
        assertEquals(50.46, report.overall!!.mspt, 0.0001)
    }

    @Test
    fun matchesDimensionByAliasWithoutInventingOverall() {
        val descriptor = DimensionDescriptor(
            id = -1,
            displayName = "Nether",
            aliases = setOf("nether", "the nether", "hell")
        )

        val report = OpisTpsTextParser.parse(
            listOf(
                "Nether: 20.00 TPS · 35.50 ms/t",
                "Chunk Manager TPS: 19.90"
            ),
            descriptor
        )

        assertNull(report.overall)
        assertNotNull(report.currentDimension)
        assertEquals(20.0, report.currentDimension!!.tps, 0.0001)
        assertEquals(35.5, report.currentDimension!!.mspt, 0.0001)
    }

    @Test
    fun ignoresSubsystemLinesWithoutServerOrDimensionContext() {
        assertTrue(OpisTpsTextParser.parse(listOf("Players online: 5"), null).overall == null)
        assertFalse(OpisTpsTextParser.looksLikeOpisLine("Chunk Manager TPS: 19.90"))
    }
}



