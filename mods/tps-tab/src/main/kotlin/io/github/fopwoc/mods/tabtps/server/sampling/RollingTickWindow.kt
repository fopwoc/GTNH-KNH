package io.github.fopwoc.mods.tabtps.server.sampling

import kotlin.math.min

object RollingTickWindow {
  private const val NANOS_PER_MILLISECOND = 1_000_000.0
  private const val TARGET_TPS = 20.0
  private const val MILLIS_PER_SECOND = 1_000.0

  fun averageMilliseconds(
      samples: LongArray,
      currentIndex: Int,
      sampleCount: Int = 20,
  ): Double? {
    if (samples.isEmpty() || sampleCount <= 0) {
      return null
    }

    var totalNanos = 0L
    var populatedSamples = 0
    val samplesToRead = min(sampleCount, samples.size)
    for (offset in 0 until samplesToRead) {
      val index = Math.floorMod(currentIndex - offset, samples.size)
      val sample = samples[index]
      if (sample > 0L) {
        totalNanos += sample
        populatedSamples++
      }
    }

    return if (populatedSamples == 0) {
      null
    } else {
      totalNanos.toDouble() / populatedSamples / NANOS_PER_MILLISECOND
    }
  }

  fun tpsFor(mspt: Double): Double =
      if (mspt <= 0.0) TARGET_TPS else min(TARGET_TPS, MILLIS_PER_SECOND / mspt)
}
