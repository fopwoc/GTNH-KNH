package io.github.fopwoc.mods.framework.ui.compose.model.color

import androidx.compose.runtime.Stable

@Stable
data class Color(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int = 0xFF,
) {
  constructor(
      value: Int
  ) : this(
      red = value ushr 16 and 0xFF,
      green = value ushr 8 and 0xFF,
      blue = value and 0xFF,
      alpha = value ushr 24 and 0xFF,
  )

  constructor(value: Long) : this(requirePackedArgb(value).toInt())

  init {
    requireChannel("red", red)
    requireChannel("green", green)
    requireChannel("blue", blue)
    requireChannel("alpha", alpha)
  }

  val argbInt: Int
    get() =
        ((alpha and 0xFF) shl 24) or
            ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or
            (blue and 0xFF)

  val rgbInt: Int
    get() = ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)

  companion object {
    val Transparent: Color = Color(red = 0x00, green = 0x00, blue = 0x00, alpha = 0x00)

    fun rgb(red: Int, green: Int, blue: Int): Color {
      return Color(red = red, green = green, blue = blue)
    }

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Color {
      return Color(red = red, green = green, blue = blue, alpha = alpha)
    }

    private fun requireChannel(name: String, value: Int) {
      require(value in 0..0xFF) {
        "$name must be in 0..255, but was $value"
      }
    }

    private fun requirePackedArgb(value: Long): Long {
      require(value in 0L..0xFFFFFFFFL) {
        "Packed color must be in 0x00000000..0xFFFFFFFF, but was 0x${value.toString(16)}"
      }
      return value
    }
  }
}
