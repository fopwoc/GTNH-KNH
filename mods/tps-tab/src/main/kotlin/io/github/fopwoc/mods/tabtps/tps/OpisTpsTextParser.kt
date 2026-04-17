package io.github.fopwoc.mods.tabtps.tps

import kotlin.math.max
import kotlin.math.min

object OpisTpsTextParser {
    private val whitespacePattern = Regex("""\s+""")
    private val tpsValuePatterns = listOf(
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*tps""", RegexOption.IGNORE_CASE),
        Regex("""tps\s*[:=]?[ ]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    )
    private val msptValuePatterns = listOf(
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:ms(?:/t|pt)?|mspt)""", RegexOption.IGNORE_CASE),
        Regex("""(?:ms(?:/t|pt)?|mspt)\s*[:=]?[ ]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE),
        Regex("""tick(?:\s+time)?\s*[:=]?[ ]*([0-9]+(?:\.[0-9]+)?)\s*ms""", RegexOption.IGNORE_CASE)
    )
    private val overallKeywords = listOf("overall", "server", "global", "total")

    fun looksLikeOpisLine(rawLine: String): Boolean {
        val normalized = normalize(rawLine)
        if (!normalized.contains("tps")) {
            return false
        }

        return containsOverallKeyword(normalized)
            || normalized.contains("dimension")
            || normalized.contains("dim ")
            || normalized.contains("mspt")
            || normalized.contains("ms/t")
            || normalized.contains("tick time")
    }

    fun parse(lines: List<String>, descriptor: DimensionDescriptor?): ParsedOpisReport {
        val parsedLines = lines
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull(::parseLine)
            .toList()

        if (parsedLines.isEmpty()) {
            return ParsedOpisReport()
        }

        val dimensionLine = descriptor?.let { current ->
            parsedLines.firstOrNull { line -> matchesDimension(line.normalized, current) }
        }

        val overallLine = parsedLines.firstOrNull { line -> containsOverallKeyword(line.normalized) }

        return ParsedOpisReport(
            overall = overallLine?.measurement,
            currentDimension = dimensionLine?.measurement,
            overallLine = overallLine?.raw,
            dimensionLine = dimensionLine?.raw
        )
    }

    private fun parseLine(rawLine: String): ParsedLine? {
        if (!looksLikeOpisLine(rawLine)) {
            return null
        }

        val normalized = normalize(rawLine)
        val tps = extractValue(rawLine, tpsValuePatterns)
        val mspt = extractValue(rawLine, msptValuePatterns)
        if (tps == null && mspt == null) {
            return null
        }

        val resolvedMspt = mspt ?: tps?.let { 1000.0 / max(it, 0.0001) }
        val resolvedTps = tps ?: resolvedMspt?.let { min(20.0, 1000.0 / max(it, 0.0001)) }
        if (resolvedMspt == null || resolvedTps == null) {
            return null
        }

        return ParsedLine(
            raw = rawLine,
            normalized = normalized,
            measurement = TpsMeasurement(
                tps = resolvedTps,
                mspt = resolvedMspt
            )
        )
    }

    private fun extractValue(rawLine: String, patterns: List<Regex>): Double? {
        return patterns.asSequence()
            .mapNotNull { pattern -> pattern.find(rawLine)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
            .firstOrNull()
    }

    private fun containsOverallKeyword(normalized: String): Boolean {
        return overallKeywords.any(normalized::contains)
    }

    private fun matchesDimension(normalized: String, descriptor: DimensionDescriptor): Boolean {
        val id = descriptor.id.toString()
        val aliases = descriptor.aliases.map(::normalize).filter(String::isNotBlank)

        if (Regex("""\bdim(?:ension)?\s*[:=#-]?\s*${Regex.escape(id)}\b""").containsMatchIn(normalized)) {
            return true
        }

        if (normalized.contains("dimension $id") || normalized.contains("dim $id")) {
            return true
        }

        return aliases.any(normalized::contains)
    }

    private fun normalize(rawLine: String): String {
        return rawLine
            .lowercase()
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('(', ' ')
            .replace(')', ' ')
            .replace(':', ' ')
            .replace(',', ' ')
            .replace('=', ' ')
            .replace(whitespacePattern, " ")
            .trim()
    }

    private data class ParsedLine(
        val raw: String,
        val normalized: String,
        val measurement: TpsMeasurement
    )
}



