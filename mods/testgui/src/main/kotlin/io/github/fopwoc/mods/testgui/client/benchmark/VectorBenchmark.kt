package io.github.fopwoc.mods.testgui.client.benchmark

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class VectorBenchmarkResult(
    val pixels: Int,
    val passesPerSample: Int,
    val lanes: Int,
    val scalarMedianNs: Long,
    val vectorMedianNs: Long,
    val checksum: Int,
) {
  val speedup: Double
    get() = scalarMedianNs.toDouble() / vectorMedianNs
}

/** Runs only after the launcher has resolved the incubating module into the boot layer. */
object VectorBenchmark {
  private const val MODULE = "jdk.incubator.vector"
  private const val PIXELS = 1 shl 20
  private const val PASSES = 32
  private const val SAMPLES = 5

  @Volatile private var consumed = 0

  fun available(): Boolean = ModuleLayer.boot().findModule(MODULE).isPresent

  suspend fun run(): VectorBenchmarkResult {
    check(available()) { "Launch Java with --add-modules=jdk.incubator.vector" }

    val input = IntArray(PIXELS)
    var seed = 0x1234abcd
    for (index in input.indices) {
      seed = seed * 1664525 + 1013904223
      input[index] = seed
    }
    val scalarOutput = IntArray(PIXELS)
    val vectorOutput = IntArray(PIXELS)

    repeat(4) {
      VectorKernel.scalar(input, scalarOutput)
      VectorKernel.vector(input, vectorOutput)
    }
    check(scalarOutput.contentEquals(vectorOutput)) { "Vector output differs from scalar output" }

    val scalarTimes = LongArray(SAMPLES)
    val vectorTimes = LongArray(SAMPLES)
    repeat(SAMPLES) { sample ->
      currentCoroutineContext().ensureActive()
      if (sample % 2 == 0) {
        scalarTimes[sample] = time(PASSES) { VectorKernel.scalar(input, scalarOutput) }
        vectorTimes[sample] = time(PASSES) { VectorKernel.vector(input, vectorOutput) }
      } else {
        vectorTimes[sample] = time(PASSES) { VectorKernel.vector(input, vectorOutput) }
        scalarTimes[sample] = time(PASSES) { VectorKernel.scalar(input, scalarOutput) }
      }
      consumed = scalarOutput[sample] xor vectorOutput[PIXELS - 1 - sample]
    }
    check(scalarOutput.contentEquals(vectorOutput)) { "Vector output differs after timing" }

    return VectorBenchmarkResult(
        pixels = PIXELS,
        passesPerSample = PASSES,
        lanes = VectorKernel.lanes,
        scalarMedianNs = scalarTimes.sorted()[SAMPLES / 2],
        vectorMedianNs = vectorTimes.sorted()[SAMPLES / 2],
        checksum = scalarOutput.fold(0) { checksum, pixel -> checksum xor pixel },
    )
  }

  private inline fun time(passes: Int, operation: () -> Unit): Long {
    val started = System.nanoTime()
    repeat(passes) { operation() }
    return System.nanoTime() - started
  }
}
