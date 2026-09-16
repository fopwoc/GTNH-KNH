package io.github.fopwoc.mods.testgui.client.benchmark

import kotlin.test.Test
import kotlin.test.assertContentEquals

class VectorKernelTest {
  @Test
  fun surfaceAndLodMatchAcrossLaneBoundaries() {
    val lanes = VectorKernel.lanes
    for (width in listOf(1, 2, 3, 4, lanes - 1, lanes, lanes + 1, 17)) {
      val size = width * width
      val colors = IntArray(size) { index -> (index * 0x12345) xor 0x6d37a4c2 }
      val heights = IntArray(size) { index -> 64 + (index * 7 % 50) }
      val tints = IntArray(size) { index -> (index * 0x23457) xor 0x00b8d0e2 }
      val scalar = IntArray(size)
      val vector = IntArray(size)

      VectorKernel.surfaceScalar(colors, heights, tints, width, scalar)
      VectorKernel.surfaceVector(colors, heights, tints, width, vector)
      assertContentEquals(scalar, vector, "surface width=$width, lanes=$lanes")

      if (width >= 2) {
        val indexMap = VectorKernel.lodIndexMap(width)
        val scalarLod = IntArray(indexMap.size)
        val vectorLod = IntArray(indexMap.size)
        VectorKernel.lodScalar(scalar, width, scalarLod)
        VectorKernel.lodVector(vector, width, indexMap, vectorLod)
        assertContentEquals(scalarLod, vectorLod, "LOD width=$width, lanes=$lanes")
      }
    }
  }
}
