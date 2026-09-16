package io.github.fopwoc.mods.testgui.client.benchmark

import kotlin.test.Test
import kotlin.test.assertContentEquals

class VectorKernelTest {
  @Test
  fun vectorMatchesScalarAcrossLaneBoundaries() {
    val lanes = VectorKernel.lanes
    for (size in listOf(0, 1, lanes - 1, lanes, lanes + 1, lanes * 3 + 2, 257)) {
      val input =
          IntArray(size) { index ->
            (index * 0x12345) xor (index shl 24) xor 0x6d37a4c2
          }
      val scalar = IntArray(size)
      val vector = IntArray(size)

      VectorKernel.scalar(input, scalar)
      VectorKernel.vector(input, vector)

      assertContentEquals(scalar, vector, "length=$size, lanes=$lanes")
    }
  }
}
