package io.github.fopwoc.mods.testgui.client.benchmark

import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators

/** The same integer grayscale conversion as [scalar], expressed with explicit SIMD lanes. */
internal object VectorKernel {
  private val species = IntVector.SPECIES_PREFERRED

  val lanes: Int
    get() = species.length()

  fun scalar(input: IntArray, output: IntArray) {
    for (index in input.indices) {
      val pixel = input[index]
      val red = (pixel ushr 16) and 0xff
      val green = (pixel ushr 8) and 0xff
      val blue = pixel and 0xff
      val gray = (red * 77 + green * 150 + blue * 29 + 128) ushr 8
      output[index] = (pixel and 0xff000000.toInt()) or (gray shl 16) or (gray shl 8) or gray
    }
  }

  fun vector(input: IntArray, output: IntArray) {
    val upperBound = species.loopBound(input.size)
    var index = 0
    while (index < upperBound) {
      val pixels = IntVector.fromArray(species, input, index)
      val red = pixels.lanewise(VectorOperators.LSHR, 16).and(0xff)
      val green = pixels.lanewise(VectorOperators.LSHR, 8).and(0xff)
      val blue = pixels.and(0xff)
      val gray =
          red.mul(77)
              .add(green.mul(150))
              .add(blue.mul(29))
              .add(128)
              .lanewise(VectorOperators.LSHR, 8)
      val result =
          pixels
              .and(0xff000000.toInt())
              .or(gray.lanewise(VectorOperators.LSHL, 16))
              .or(gray.lanewise(VectorOperators.LSHL, 8))
              .or(gray)
      result.intoArray(output, index)
      index += species.length()
    }

    // A scalar tail keeps the kernel correct for arbitrary array lengths.
    for (tail in index until input.size) {
      val pixel = input[tail]
      val red = (pixel ushr 16) and 0xff
      val green = (pixel ushr 8) and 0xff
      val blue = pixel and 0xff
      val gray = (red * 77 + green * 150 + blue * 29 + 128) ushr 8
      output[tail] = (pixel and 0xff000000.toInt()) or (gray shl 16) or (gray shl 8) or gray
    }
  }
}
