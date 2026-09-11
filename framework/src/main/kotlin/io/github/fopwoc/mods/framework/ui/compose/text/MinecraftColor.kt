package io.github.fopwoc.mods.framework.ui.compose.text

import androidx.compose.runtime.Stable
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

@Stable
enum class MinecraftColor(
    internal val formattingCode: Char,
    val color: Color,
) {
  Black('0', Color(0xFF000000)),
  DarkBlue('1', Color(0xFF0000AA)),
  DarkGreen('2', Color(0xFF00AA00)),
  DarkAqua('3', Color(0xFF00AAAA)),
  DarkRed('4', Color(0xFFAA0000)),
  DarkPurple('5', Color(0xFFAA00AA)),
  Gold('6', Color(0xFFFFAA00)),
  Gray('7', Color(0xFFAAAAAA)),
  DarkGray('8', Color(0xFF555555)),
  Blue('9', Color(0xFF5555FF)),
  Green('a', Color(0xFF55FF55)),
  Aqua('b', Color(0xFF55FFFF)),
  Red('c', Color(0xFFFF5555)),
  LightPurple('d', Color(0xFFFF55FF)),
  Yellow('e', Color(0xFFFFFF55)),
  White('f', Color(0xFFFFFFFF));

  internal val controlString: String
    get() = "\u00a7$formattingCode"
}
