package io.github.fopwoc.mods.framework.ui.compose.text

import androidx.compose.runtime.Stable

@Stable
data class StyledTextSpanStyle(
    val color: MinecraftColor? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
) {
  internal val isDefault: Boolean
    get() = color == null && !bold && !italic && !underline && !strikethrough && !obfuscated

  internal fun toFormattingPrefix(reset: Boolean): String {
    if (isDefault) {
      return if (reset) "\u00a7r" else ""
    }

    return buildString {
      if (reset) {
        append("\u00a7r")
      }
      color?.let { append(it.controlString) }
      if (obfuscated) append("\u00a7k")
      if (bold) append("\u00a7l")
      if (strikethrough) append("\u00a7m")
      if (underline) append("\u00a7n")
      if (italic) append("\u00a7o")
    }
  }
}

@Stable
data class StyledTextSpan(
    val text: String,
    val style: StyledTextSpanStyle = StyledTextSpanStyle(),
)

@Stable
class StyledText internal constructor(spans: List<StyledTextSpan>) {
  val spans: List<StyledTextSpan> = spans.filter { it.text.isNotEmpty() }.toList()

  val plainText: String
    get() = spans.joinToString(separator = "") { it.text }

  val formattedString: String
    get() = buildFormattedString(spans)

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is StyledText) {
      return false
    }

    return spans == other.spans
  }

  override fun hashCode(): Int = spans.hashCode()

  override fun toString(): String = "StyledText(spans=$spans)"

  companion object {
    val Empty: StyledText = StyledText(emptyList())

    fun of(text: String): StyledText {
      return if (text.isEmpty()) {
        Empty
      } else {
        StyledText(listOf(StyledTextSpan(text)))
      }
    }
  }
}

fun styledText(block: StyledTextBuilder.() -> Unit): StyledText {
  return StyledTextBuilder().apply(block).build()
}

class StyledTextBuilder {
  private val spans = mutableListOf<StyledTextSpan>()
  private var currentStyle: StyledTextSpanStyle = StyledTextSpanStyle()

  fun append(text: String) {
    if (text.isEmpty()) {
      return
    }

    appendSpan(StyledTextSpan(text, currentStyle))
  }

  fun append(text: StyledText) {
    text.spans.forEach(::appendSpan)
  }

  operator fun String.unaryPlus() {
    append(this)
  }

  operator fun StyledText.unaryPlus() {
    append(this)
  }

  fun withColor(color: MinecraftColor, block: StyledTextBuilder.() -> Unit) {
    withStyle(currentStyle.copy(color = color), block)
  }

  fun withBold(block: StyledTextBuilder.() -> Unit) {
    withStyle(currentStyle.copy(bold = true), block)
  }

  fun withItalic(block: StyledTextBuilder.() -> Unit) {
    withStyle(currentStyle.copy(italic = true), block)
  }

  fun withUnderline(block: StyledTextBuilder.() -> Unit) {
    withStyle(currentStyle.copy(underline = true), block)
  }

  fun withStrikethrough(block: StyledTextBuilder.() -> Unit) {
    withStyle(currentStyle.copy(strikethrough = true), block)
  }

  fun withObfuscated(block: StyledTextBuilder.() -> Unit) {
    withStyle(currentStyle.copy(obfuscated = true), block)
  }

  fun withReset(block: StyledTextBuilder.() -> Unit) {
    withStyle(StyledTextSpanStyle(), block)
  }

  fun build(): StyledText {
    return if (spans.isEmpty()) {
      StyledText.Empty
    } else {
      StyledText(spans.toList())
    }
  }

  private fun withStyle(style: StyledTextSpanStyle, block: StyledTextBuilder.() -> Unit) {
    val previousStyle = currentStyle
    currentStyle = style
    try {
      block()
    } finally {
      currentStyle = previousStyle
    }
  }

  private fun appendSpan(span: StyledTextSpan) {
    if (span.text.isEmpty()) {
      return
    }

    val lastSpan = spans.lastOrNull()
    if (lastSpan != null && lastSpan.style == span.style) {
      spans[spans.lastIndex] = lastSpan.copy(text = lastSpan.text + span.text)
    } else {
      spans += span
    }
  }
}

private fun buildFormattedString(spans: List<StyledTextSpan>): String {
  if (spans.isEmpty()) {
    return ""
  }

  return buildString {
    var previousStyle = StyledTextSpanStyle()
    spans.forEachIndexed { index, span ->
      if (span.style != previousStyle) {
        append(span.style.toFormattingPrefix(reset = index > 0 && !previousStyle.isDefault))
        previousStyle = span.style
      }
      append(span.text)
    }
  }
}
