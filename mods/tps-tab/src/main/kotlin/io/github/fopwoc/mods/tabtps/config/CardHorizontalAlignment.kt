package io.github.fopwoc.mods.tabtps.config

import java.util.Locale

enum class CardHorizontalAlignment {
  LEFT,
  CENTER,
  RIGHT;

  val configValue: String
    get() = name.lowercase(Locale.ROOT)

  companion object {
    fun fromConfig(value: String): CardHorizontalAlignment =
        entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CENTER
  }
}
