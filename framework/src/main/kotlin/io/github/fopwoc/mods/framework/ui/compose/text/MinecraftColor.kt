package io.github.fopwoc.mods.framework.ui.compose.text

import androidx.compose.runtime.Stable
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

@Stable
enum class MinecraftColor(
    internal val formattingCode: Char,
    val color: Color,
) {
  Black('0', Color.rgb(red = 0x00, green = 0x00, blue = 0x00)),
  DarkBlue('1', Color.rgb(red = 0x00, green = 0x00, blue = 0xAA)),
  DarkGreen('2', Color.rgb(red = 0x00, green = 0xAA, blue = 0x00)),
  DarkAqua('3', Color.rgb(red = 0x00, green = 0xAA, blue = 0xAA)),
  DarkRed('4', Color.rgb(red = 0xAA, green = 0x00, blue = 0x00)),
  DarkPurple('5', Color.rgb(red = 0xAA, green = 0x00, blue = 0xAA)),
  Gold('6', Color.rgb(red = 0xFF, green = 0xAA, blue = 0x00)),
  Gray('7', Color.rgb(red = 0xAA, green = 0xAA, blue = 0xAA)),
  DarkGray('8', Color.rgb(red = 0x55, green = 0x55, blue = 0x55)),
  Blue('9', Color.rgb(red = 0x55, green = 0x55, blue = 0xFF)),
  Green('a', Color.rgb(red = 0x55, green = 0xFF, blue = 0x55)),
  Aqua('b', Color.rgb(red = 0x55, green = 0xFF, blue = 0xFF)),
  Red('c', Color.rgb(red = 0xFF, green = 0x55, blue = 0x55)),
  LightPurple('d', Color.rgb(red = 0xFF, green = 0x55, blue = 0xFF)),
  Yellow('e', Color.rgb(red = 0xFF, green = 0xFF, blue = 0x55)),
  White('f', Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF));

  internal val controlString: String
    get() = "\u00a7$formattingCode"
}
