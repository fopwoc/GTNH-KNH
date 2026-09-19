package io.github.fopwoc.mods.palimpsest.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapStorageEstimateTest {
    @Test
    fun aShorterIntervalCostsProportionallyMore() {
        val minute = MapStorageEstimate.bytesPerDay(60)
        val second = MapStorageEstimate.bytesPerDay(1)
        assertEquals(60.0, second / minute, 1e-9)
        assertTrue(minute in 500_000.0..2_000_000.0, "a minute-interval day is ~1 MB, was $minute")
    }

    @Test
    fun describesADayInReadableUnits() {
        assertEquals("24 h of play ≈ 956 KB", MapStorageEstimate.describeDay(60))
        assertEquals("24 h of play ≈ 56 MB", MapStorageEstimate.describeDay(1))
        assertEquals("1.5 GB", MapStorageEstimate.formatBytes(1.5 * 1024 * 1024 * 1024))
        assertEquals("12 B", MapStorageEstimate.formatBytes(12.0))
    }
}
