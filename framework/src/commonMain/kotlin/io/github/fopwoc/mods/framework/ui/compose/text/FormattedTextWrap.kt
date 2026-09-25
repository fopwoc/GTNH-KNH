package io.github.fopwoc.mods.framework.ui.compose.text

/**
 * Word wrap for text with `§` formatting codes, as 1.7.10's `listFormattedStringToWidth` does it:
 * lines break at the last space that fits (or mid-word when a word alone is too wide), the space is
 * dropped, and each continuation line starts with the colour and styles still active.
 */
internal object FormattedTextWrap {
    private const val FORMAT = '§'

    fun wrap(text: String, maxWidth: Int, width: (String) -> Int): List<String> =
        text.split('\n').flatMap { paragraph -> wrapParagraph(paragraph, maxWidth, width) }

    private fun wrapParagraph(
        paragraph: String,
        maxWidth: Int,
        width: (String) -> Int,
    ): List<String> {
        if (paragraph.isEmpty() || width(paragraph) <= maxWidth) return listOf(paragraph)
        val lines = mutableListOf<String>()
        var remaining = paragraph
        while (width(remaining) > maxWidth) {
            val fit = fittingLength(remaining, maxWidth, width)
            val space = remaining.lastIndexOf(' ', fit)
            val end = if (space > 0) space else fit
            val line = remaining.substring(0, end)
            lines += line
            val rest =
                remaining.substring(end).let { if (it.startsWith(' ')) it.substring(1) else it }
            remaining = activeFormatting(line) + rest
            if (rest.isEmpty()) return lines
        }
        lines += remaining
        return lines
    }

    /**
     * Longest prefix within [maxWidth] that keeps codes whole and holds at least one visible char.
     */
    private fun fittingLength(text: String, maxWidth: Int, width: (String) -> Int): Int {
        var fit = 0
        var index = 0
        while (index < text.length) {
            val next =
                if (text[index] == FORMAT && index + 1 < text.length) index + 2 else index + 1
            if (text[index] != FORMAT && fit > 0 && width(text.substring(0, next)) > maxWidth) break
            index = next
            if (text[index - 1] != FORMAT && (index < 2 || text[index - 2] != FORMAT)) fit = index
        }
        return fit.coerceAtLeast(1)
    }

    /** The colour and style codes in effect at the end of [text], as a prefix for the next line. */
    fun activeFormatting(text: String): String {
        var colour = ""
        val styles = StringBuilder()
        var index = text.indexOf(FORMAT)
        while (index >= 0 && index + 1 < text.length) {
            val code = text[index + 1].lowercaseChar()
            when (code) {
                in '0'..'9',
                in 'a'..'f' -> {
                    colour = "$FORMAT$code"
                    styles.clear()
                }
                'r' -> {
                    colour = ""
                    styles.clear()
                }
                in 'k'..'o' -> styles.append(FORMAT).append(code)
            }
            index = text.indexOf(FORMAT, index + 2)
        }
        return colour + styles
    }
}
