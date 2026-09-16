package io.github.fopwoc.mods.testgui.client.benchmark

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class VectorBenchmarkResult(
    val sourceWidth: Int,
    val lodLevels: Int,
    val passesPerSample: Int,
    val lanes: Int,
    val scalarSurfaceNs: Long,
    val vectorSurfaceNs: Long,
    val scalarLodNs: Long,
    val vectorLodNs: Long,
    val scalarTotalNs: Long,
    val vectorTotalNs: Long,
    val checksum: Int,
) {
  val speedup: Double
    get() = scalarTotalNs.toDouble() / vectorTotalNs
}

/** A synthetic 64x64-chunk surface and its full image pyramid; no Minecraft world is read. */
object VectorBenchmark {
  private const val MODULE = "jdk.incubator.vector"
  private const val WIDTH = 1024
  private const val PASSES = 16
  private const val SAMPLES = 5

  @Volatile private var consumed = 0

  fun available(): Boolean = ModuleLayer.boot().findModule(MODULE).isPresent

  suspend fun run(): VectorBenchmarkResult {
    check(available()) { "Launch Java with --add-modules=jdk.incubator.vector" }

    val colors = IntArray(WIDTH * WIDTH)
    val heights = IntArray(colors.size)
    val tints = IntArray(colors.size)
    var seed = 0x1234abcd
    for (index in colors.indices) {
      seed = seed * 1664525 + 1013904223
      val x = index % WIDTH
      val y = index / WIDTH
      colors[index] =
          when ((seed ushr 27) and 3) {
            0 -> 0x657b45 // grass
            1 -> 0x837e72 // stone
            2 -> 0x487baf // water
            else -> 0xb59a6b // sand
          }
      heights[index] = 64 + ((x / 16 + y / 24) and 31) + ((seed ushr 29) and 3)
      tints[index] =
          when ((x / 64 + y / 64) and 3) {
            0 -> 0xffffff
            1 -> 0xc8e8bb
            2 -> 0xdbe7f6
            else -> 0xf1d4ad
          }
    }

    val scalar = allocatePyramid()
    val vector = allocatePyramid()
    val indexMaps = buildList {
      var width = WIDTH
      while (width > 1) {
        add(VectorKernel.lodIndexMap(width))
        width /= 2
      }
    }

    repeat(3) {
      surfaceAndLods(colors, heights, tints, scalar, indexMaps, false)
      surfaceAndLods(colors, heights, tints, vector, indexMaps, true)
    }
    verifyEqual(scalar, vector)

    val scalarSurface = LongArray(SAMPLES)
    val vectorSurface = LongArray(SAMPLES)
    val scalarLod = LongArray(SAMPLES)
    val vectorLod = LongArray(SAMPLES)
    repeat(SAMPLES) { sample ->
      currentCoroutineContext().ensureActive()
      if (sample % 2 == 0) {
        val a = measure(colors, heights, tints, scalar, indexMaps, false)
        val b = measure(colors, heights, tints, vector, indexMaps, true)
        scalarSurface[sample] = a.surfaceNs
        scalarLod[sample] = a.lodNs
        vectorSurface[sample] = b.surfaceNs
        vectorLod[sample] = b.lodNs
      } else {
        val b = measure(colors, heights, tints, vector, indexMaps, true)
        val a = measure(colors, heights, tints, scalar, indexMaps, false)
        scalarSurface[sample] = a.surfaceNs
        scalarLod[sample] = a.lodNs
        vectorSurface[sample] = b.surfaceNs
        vectorLod[sample] = b.lodNs
      }
      consumed = scalar.first()[sample] xor vector.last()[0]
    }
    verifyEqual(scalar, vector)

    return VectorBenchmarkResult(
        sourceWidth = WIDTH,
        lodLevels = scalar.size - 1,
        passesPerSample = PASSES,
        lanes = VectorKernel.lanes,
        scalarSurfaceNs = scalarSurface.sorted()[SAMPLES / 2],
        vectorSurfaceNs = vectorSurface.sorted()[SAMPLES / 2],
        scalarLodNs = scalarLod.sorted()[SAMPLES / 2],
        vectorLodNs = vectorLod.sorted()[SAMPLES / 2],
        scalarTotalNs =
            LongArray(SAMPLES) { scalarSurface[it] + scalarLod[it] }.sorted()[SAMPLES / 2],
        vectorTotalNs =
            LongArray(SAMPLES) { vectorSurface[it] + vectorLod[it] }.sorted()[SAMPLES / 2],
        checksum =
            scalar.fold(0) { total, level -> level.fold(total) { sum, pixel -> sum xor pixel } },
    )
  }

  private fun allocatePyramid(): List<IntArray> = buildList {
    var width = WIDTH
    while (width >= 1) {
      add(IntArray(width * width))
      width /= 2
    }
  }

  private fun surfaceAndLods(
      colors: IntArray,
      heights: IntArray,
      tints: IntArray,
      pyramid: List<IntArray>,
      indexMaps: List<IntArray>,
      useVector: Boolean,
  ) {
    if (useVector) VectorKernel.surfaceVector(colors, heights, tints, WIDTH, pyramid[0])
    else VectorKernel.surfaceScalar(colors, heights, tints, WIDTH, pyramid[0])
    var width = WIDTH
    for (level in 1 until pyramid.size) {
      if (useVector)
          VectorKernel.lodVector(pyramid[level - 1], width, indexMaps[level - 1], pyramid[level])
      else VectorKernel.lodScalar(pyramid[level - 1], width, pyramid[level])
      width /= 2
    }
  }

  private fun measure(
      colors: IntArray,
      heights: IntArray,
      tints: IntArray,
      pyramid: List<IntArray>,
      indexMaps: List<IntArray>,
      useVector: Boolean,
  ): StageTimes {
    var surfaceNs = 0L
    var lodNs = 0L
    repeat(PASSES) {
      val surfaceStart = System.nanoTime()
      if (useVector) VectorKernel.surfaceVector(colors, heights, tints, WIDTH, pyramid[0])
      else VectorKernel.surfaceScalar(colors, heights, tints, WIDTH, pyramid[0])
      surfaceNs += System.nanoTime() - surfaceStart

      val lodStart = System.nanoTime()
      var width = WIDTH
      for (level in 1 until pyramid.size) {
        if (useVector)
            VectorKernel.lodVector(pyramid[level - 1], width, indexMaps[level - 1], pyramid[level])
        else VectorKernel.lodScalar(pyramid[level - 1], width, pyramid[level])
        width /= 2
      }
      lodNs += System.nanoTime() - lodStart
    }
    return StageTimes(surfaceNs, lodNs)
  }

  private fun verifyEqual(scalar: List<IntArray>, vector: List<IntArray>) {
    scalar.indices.forEach { level ->
      check(scalar[level].contentEquals(vector[level])) { "Vector output differs at LOD $level" }
    }
  }

  private data class StageTimes(val surfaceNs: Long, val lodNs: Long)
}
