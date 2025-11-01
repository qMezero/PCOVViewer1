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

internal fun normalizeConnectionCode(rawCode: String): String {
    val trimmed = rawCode.trim()
    if (trimmed.isEmpty() || trimmed.contains("..")) {
        return trimmed
    }

    val leadingDigits = buildString {
        for (character in trimmed) {
            if (character.isDigit()) {
                append(character)
            } else {
                break
            }
        }
    }

    if (leadingDigits.isEmpty()) {
        return trimmed
    }

    val remainder = trimmed.substring(leadingDigits.length)
    if (!remainder.startsWith(".")) {
        return trimmed
    }

    val suffix = remainder.drop(1)
    if (suffix.isEmpty()) {
        return "$leadingDigits.."
    }

    if (!suffix.first().isDigit()) {
        return trimmed
    }

    return buildString {
        append(leadingDigits)
        append("..")
        append(suffix)
    }
}
