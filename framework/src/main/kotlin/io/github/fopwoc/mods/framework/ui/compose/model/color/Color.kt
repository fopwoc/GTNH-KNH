package io.github.fopwoc.mods.framework.ui.compose.model.color

import androidx.compose.runtime.Stable

@Stable
data class Color(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int = MAX_CHANNEL_VALUE,
) {
    constructor(
        value: Int
    ) : this(
        red = value ushr 16 and MAX_CHANNEL_VALUE,
        green = value ushr 8 and MAX_CHANNEL_VALUE,
        blue = value and MAX_CHANNEL_VALUE,
        alpha = value ushr 24 and MAX_CHANNEL_VALUE,
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
            ((alpha and MAX_CHANNEL_VALUE) shl 24) or
                ((red and MAX_CHANNEL_VALUE) shl 16) or
                ((green and MAX_CHANNEL_VALUE) shl 8) or
                (blue and MAX_CHANNEL_VALUE)

    val rgbInt: Int
        get() =
            ((red and MAX_CHANNEL_VALUE) shl 16) or
                ((green and MAX_CHANNEL_VALUE) shl 8) or
                (blue and MAX_CHANNEL_VALUE)

    companion object {
        private const val MAX_CHANNEL_VALUE: Int = 0xFF
        private const val MAX_PACKED_ARGB_VALUE: Long = 0xFFFFFFFFL

        val Transparent: Color = Color(red = 0x00, green = 0x00, blue = 0x00, alpha = 0x00)

        fun rgb(red: Int, green: Int, blue: Int): Color {
            return Color(red = red, green = green, blue = blue)
        }

        fun argb(alpha: Int, red: Int, green: Int, blue: Int): Color {
            return Color(red = red, green = green, blue = blue, alpha = alpha)
        }

        private fun requireChannel(name: String, value: Int) {
            require(value in 0..MAX_CHANNEL_VALUE) {
                "$name must be in 0..255, but was $value"
            }
        }

        private fun requirePackedArgb(value: Long): Long {
            require(value in 0L..MAX_PACKED_ARGB_VALUE) {
                "Packed color must be in 0x00000000..0xFFFFFFFF, but was 0x${value.toString(16)}"
            }
            return value
        }
    }
}
