package io.github.fopwoc.mods.testgui.client.benchmark

import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators

/** Synthetic chunk columns to ARGB surface pixels, followed by 2x2 LOD reduction. */
internal object VectorKernel {
  private val species = IntVector.SPECIES_PREFERRED

  val lanes: Int
    get() = species.length()

  fun surfaceScalar(
      colors: IntArray,
      heights: IntArray,
      tints: IntArray,
      width: Int,
      output: IntArray,
  ) {
    for (index in output.indices) {
      val northHeight = if (index < width) heights[index] else heights[index - width]
      output[index] = surfacePixel(colors[index], tints[index], heights[index] - northHeight)
    }
  }

  fun surfaceVector(
      colors: IntArray,
      heights: IntArray,
      tints: IntArray,
      width: Int,
      output: IntArray,
  ) {
    val bound = species.loopBound(width)
    for (y in 0 until width) {
      val row = y * width
      var x = 0
      while (x < bound) {
        val index = row + x
        val color = IntVector.fromArray(species, colors, index)
        val tint = IntVector.fromArray(species, tints, index)
        val height = IntVector.fromArray(species, heights, index)
        val north = IntVector.fromArray(species, heights, if (y == 0) index else index - width)
        val shade =
            height
                .sub(north)
                .mul(4)
                .add(128)
                .lanewise(VectorOperators.MAX, 64)
                .lanewise(VectorOperators.MIN, 192)

        fun channel(shift: Int): IntVector {
          val base = color.lanewise(VectorOperators.LSHR, shift).and(0xff)
          val biome = tint.lanewise(VectorOperators.LSHR, shift).and(0xff)
          return base
              .mul(biome)
              .add(128)
              .lanewise(VectorOperators.LSHR, 8)
              .mul(shade)
              .add(64)
              .lanewise(VectorOperators.LSHR, 7)
              .lanewise(VectorOperators.MIN, 255)
        }

        val red = channel(16)
        val green = channel(8)
        val blue = channel(0)
        red.lanewise(VectorOperators.LSHL, 16)
            .or(green.lanewise(VectorOperators.LSHL, 8))
            .or(blue)
            .or(0xff000000.toInt())
            .intoArray(output, index)
        x += species.length()
      }
      while (x < width) {
        val index = row + x
        val northHeight = if (y == 0) heights[index] else heights[index - width]
        output[index] = surfacePixel(colors[index], tints[index], heights[index] - northHeight)
        x++
      }
    }
  }

  fun lodScalar(source: IntArray, sourceWidth: Int, output: IntArray) {
    val width = sourceWidth / 2
    for (index in output.indices) {
      val x = index % width
      val y = index / width
      val topLeft = y * 2 * sourceWidth + x * 2
      output[index] =
          average(
              source[topLeft],
              source[topLeft + 1],
              source[topLeft + sourceWidth],
              source[topLeft + sourceWidth + 1],
          )
    }
  }

  fun lodVector(source: IntArray, sourceWidth: Int, indexMap: IntArray, output: IntArray) {
    val bound = species.loopBound(output.size)
    var index = 0
    while (index < bound) {
      val a = IntVector.fromArray(species, source, 0, indexMap, index)
      val b = IntVector.fromArray(species, source, 1, indexMap, index)
      val c = IntVector.fromArray(species, source, sourceWidth, indexMap, index)
      val d = IntVector.fromArray(species, source, sourceWidth + 1, indexMap, index)

      fun channel(shift: Int): IntVector {
        fun extract(vector: IntVector) = vector.lanewise(VectorOperators.LSHR, shift).and(0xff)
        return extract(a)
            .add(extract(b))
            .add(extract(c))
            .add(extract(d))
            .add(2)
            .lanewise(VectorOperators.LSHR, 2)
      }

      channel(16)
          .lanewise(VectorOperators.LSHL, 16)
          .or(channel(8).lanewise(VectorOperators.LSHL, 8))
          .or(channel(0))
          .or(0xff000000.toInt())
          .intoArray(output, index)
      index += species.length()
    }
    while (index < output.size) {
      val topLeft = indexMap[index]
      output[index] =
          average(
              source[topLeft],
              source[topLeft + 1],
              source[topLeft + sourceWidth],
              source[topLeft + sourceWidth + 1],
          )
      index++
    }
  }

  fun lodIndexMap(sourceWidth: Int): IntArray {
    val width = sourceWidth / 2
    return IntArray(width * width) { index ->
      (index / width) * 2 * sourceWidth + (index % width) * 2
    }
  }

  private fun surfacePixel(color: Int, tint: Int, heightDelta: Int): Int {
    val shade = (128 + heightDelta * 4).coerceIn(64, 192)
    fun channel(shift: Int): Int {
      val base = (color ushr shift) and 0xff
      val biome = (tint ushr shift) and 0xff
      val tinted = (base * biome + 128) ushr 8
      return ((tinted * shade + 64) ushr 7).coerceAtMost(255)
    }
    return 0xff000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
  }

  private fun average(a: Int, b: Int, c: Int, d: Int): Int {
    fun channel(shift: Int): Int =
        (((a ushr shift) and 0xff) +
            ((b ushr shift) and 0xff) +
            ((c ushr shift) and 0xff) +
            ((d ushr shift) and 0xff) +
            2) ushr 2
    return 0xff000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
  }
}
