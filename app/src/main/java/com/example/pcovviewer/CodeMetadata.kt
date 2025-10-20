package com.example.pcovviewer

import kotlin.math.max
import kotlin.math.min

/**
 * Parsed representation of a raw point code coming from the *.pco file.
 */
data class CodeInfo(
    val baseCode: String,
    val connectsToPrevious: Boolean,
    val connectionTargets: List<Int>
) {
    companion object {
        fun parse(rawCode: String): CodeInfo {
            val trimmed = rawCode.trim()
            if (trimmed.isEmpty()) {
                return CodeInfo(baseCode = "", connectsToPrevious = false, connectionTargets = emptyList())
            }

            val parts = trimmed.split("..", limit = 2)
            val baseCode = parts.firstOrNull().orEmpty().trim().trimEnd('.')

            if (parts.size == 1) {
                return CodeInfo(baseCode = baseCode, connectsToPrevious = false, connectionTargets = emptyList())
            }

            val suffix = parts[1].trim()
            if (suffix.isEmpty()) {
                return CodeInfo(baseCode = baseCode, connectsToPrevious = true, connectionTargets = emptyList())
            }

            val targets = suffix
                .split('.')
                .mapNotNull { it.trim().toIntOrNull() }
                .distinct()

            return CodeInfo(baseCode = baseCode, connectsToPrevious = false, connectionTargets = targets)
        }
    }
}

/**
 * Contains domain rules that depend on decoded codes, such as visibility.
 */
object CodeRules {
    private val hiddenBaseCodes: Set<String> = setOf("701", "702", "703", "704", "705", "706")

    fun isHidden(point: PcoParser.PcoPoint): Boolean = isHidden(point.codeInfo.baseCode)

    fun isHidden(baseCode: String): Boolean = baseCode.isNotEmpty() && hiddenBaseCodes.contains(baseCode)
}

/**
 * Encodes a line connection between two point numbers in a deterministic order.
 */
internal fun orderedConnectionKey(first: Int, second: Int): Long {
    val start = min(first, second)
    val end = max(first, second)
    return (start.toLong() shl 32) or end.toLong()
}
