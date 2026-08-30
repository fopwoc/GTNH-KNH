package io.github.fopwoc.mods.tabtps.tps

import kotlin.math.max
import kotlin.math.min

class TimeSyncTpsEstimator {
  @Volatile private var previousSample: TimeSyncSample? = null

  @Volatile private var latestMeasurement: TimedTpsMeasurement? = null

  fun recordServerTime(
      dimensionId: Int,
      totalWorldTime: Long,
      sampledAtTick: Long,
      receivedAtNanos: Long = System.nanoTime(),
  ): TimedTpsMeasurement? {
    val sample =
        TimeSyncSample(
            dimensionId = dimensionId,
            totalWorldTime = totalWorldTime,
            receivedAtNanos = receivedAtNanos,
        )
    val previous = previousSample
    previousSample = sample

    if (previous == null || previous.dimensionId != dimensionId) {
      return latestMeasurement
    }

    val ticksElapsed = totalWorldTime - previous.totalWorldTime
    val nanosElapsed = receivedAtNanos - previous.receivedAtNanos
    if (ticksElapsed <= 0L || nanosElapsed <= 0L) {
      return latestMeasurement
    }

    val elapsedMs = nanosElapsed / 1_000_000.0
    val tps = min(20.0, ticksElapsed * 1000.0 / max(elapsedMs, 0.0001))
    val mspt = elapsedMs / ticksElapsed

    latestMeasurement =
        TimedTpsMeasurement(
            measurement =
                TpsMeasurement(
                    tps = tps,
                    mspt = mspt,
                ),
            sampledAtTick = sampledAtTick,
            source = TpsSource.TIME_SYNC_ESTIMATE,
            rawLine =
                "time-sync dimension=$dimensionId ticks=$ticksElapsed ms=${"%.2f".format(elapsedMs)}",
        )
    return latestMeasurement
  }

  fun latest(): TimedTpsMeasurement? = latestMeasurement

  fun reset() {
    previousSample = null
    latestMeasurement = null
  }

  private data class TimeSyncSample(
      val dimensionId: Int,
      val totalWorldTime: Long,
      val receivedAtNanos: Long,
  )
}
