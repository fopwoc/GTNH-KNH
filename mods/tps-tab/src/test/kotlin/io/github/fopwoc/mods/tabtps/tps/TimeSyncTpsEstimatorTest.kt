package io.github.fopwoc.mods.tabtps.tps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimeSyncTpsEstimatorTest {
    @Test
    fun estimatesTpsFromSuccessiveTimeSyncPackets() {
        val estimator = TimeSyncTpsEstimator()

        assertNull(
            estimator.recordServerTime(
                dimensionId = 0,
                totalWorldTime = 12_000,
                sampledAtTick = 100,
                receivedAtNanos = 1_000_000_000L
            )
        )

        val measurement = estimator.recordServerTime(
            dimensionId = 0,
            totalWorldTime = 12_020,
            sampledAtTick = 120,
            receivedAtNanos = 2_100_000_000L
        )

        assertNotNull(measurement)
        assertEquals(TpsSource.TIME_SYNC_ESTIMATE, measurement.source)
        assertEquals(18.1818, measurement.measurement.tps, 0.0002)
        assertEquals(55.0, measurement.measurement.mspt, 0.0001)
    }

    @Test
    fun ignoresCrossDimensionSamplesForNewEstimates() {
        val estimator = TimeSyncTpsEstimator()

        estimator.recordServerTime(
            dimensionId = 0,
            totalWorldTime = 500,
            sampledAtTick = 10,
            receivedAtNanos = 1_000_000_000L
        )

        val measurement = estimator.recordServerTime(
            dimensionId = -1,
            totalWorldTime = 520,
            sampledAtTick = 20,
            receivedAtNanos = 2_000_000_000L
        )

        assertNull(measurement)
        assertNull(estimator.latest())
    }
}

