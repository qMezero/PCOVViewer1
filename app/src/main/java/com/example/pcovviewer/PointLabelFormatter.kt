package com.example.pcovviewer

import com.example.pcovviewer.PcoParser.PcoPoint

object PointLabelFormatter {
    private val codesWithoutLabel: Set<String> = setOf("40", "42")

    fun buildLines(
        point: PcoPoint,
        showNumbers: Boolean,
        showCodes: Boolean
    ): List<String> {
        val lines = mutableListOf<String>()

        if (showNumbers) {
            lines += point.number.toString()
        }

        val rawCode = point.code.trim()
        val baseCode = point.codeInfo.baseCode
        if (showCodes && rawCode.isNotEmpty() && baseCode !in codesWithoutLabel) {
            lines += normalizeConnectionCode(rawCode)
        }
        return lines
    }
}

internal fun sanitizeConnectionCode(rawCode: String): String {
    val trimmed = rawCode.trim()
    if (trimmed.isEmpty()) {
        return trimmed
    }

    var lastAppended: Char? = null
    return buildString(trimmed.length) {
        trimmed.forEachIndexed { index, ch ->
            if (ch.isWhitespace()) {
                val next = trimmed.getOrNull(index + 1)
                if (lastAppended == '.' || next == '.') {
                    return@forEachIndexed
                }
            }

            append(ch)
            lastAppended = ch
        }
    }
}

internal fun normalizeConnectionCode(rawCode: String): String {
    val trimmed = sanitizeConnectionCode(rawCode)
    if (trimmed.isEmpty()) {
        return trimmed
    }

    val firstDotIndex = trimmed.indexOf('.')
    if (firstDotIndex < 0) {
        return trimmed
    }

    if (firstDotIndex == 0) {
        return trimmed
    }

    if (firstDotIndex + 1 < trimmed.length && trimmed[firstDotIndex + 1] == '.') {
        return trimmed
    }

    val hasNumericPrefix = trimmed.substring(0, firstDotIndex).all { it.isDigit() }
    if (!hasNumericPrefix) {
        return trimmed
    }

    return buildString(trimmed.length + 1) {
        append(trimmed.substring(0, firstDotIndex + 1))
        append('.')
        append(trimmed.substring(firstDotIndex + 1))
    }
}
