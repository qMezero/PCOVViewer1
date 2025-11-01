package com.example.pcovviewer

import kotlin.math.max
import kotlin.math.min

enum class ConnectionStyle {
    SOLID,
    DASHED
}

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
    private val hiddenBaseCodes: Set<String> = setOf(
        "70",
        "73",
        "701",
        "702",
        "703",
        "704",
        "705",
        "706",
        "731"
    )

    private val smallIconBaseCodes: Set<String> = setOf(
        "40",
        "42"
    )

    private val manualDashedBaseCodes: Set<String> = setOf(
        "30",
        "230",
        "301",
        "306"
    )

    private val manualSolidBaseCodes: Set<String> = emptySet()

    fun isHidden(point: PcoParser.PcoPoint): Boolean = isHidden(point.codeInfo.baseCode)

    fun isHidden(baseCode: String): Boolean = baseCode.isNotEmpty() && hiddenBaseCodes.contains(baseCode)

    fun pointRadiusScale(point: PcoParser.PcoPoint): Float = pointRadiusScale(point.codeInfo.baseCode)

    fun pointRadiusScale(baseCode: String): Float {
        if (baseCode.isEmpty()) {
            return 1f
        }
        return if (smallIconBaseCodes.contains(baseCode)) 0.5f else 1f
    }

    fun connectionStyleForManualTargets(
        source: PcoParser.PcoPoint,
        target: PcoParser.PcoPoint? = null
    ): ConnectionStyle {
        val sourceBase = source.codeInfo.baseCode
        if (sourceBase.isNotEmpty()) {
            if (manualDashedBaseCodes.contains(sourceBase)) {
                return ConnectionStyle.DASHED
            }
            if (manualSolidBaseCodes.contains(sourceBase)) {
                return ConnectionStyle.SOLID
            }
        }
        if (target != null) {
            val targetBase = target.codeInfo.baseCode
            if (targetBase.isNotEmpty()) {
                if (manualDashedBaseCodes.contains(targetBase)) {
                    return ConnectionStyle.DASHED
                }
                if (manualSolidBaseCodes.contains(targetBase)) {
                    return ConnectionStyle.SOLID
                }
            }
        }
        return ConnectionStyle.DASHED
    }
}

/**
 * Encodes a line connection between two point numbers in a deterministic order.
 */
internal fun orderedConnectionKey(first: Int, second: Int): Long {
    val start = min(first, second)
    val end = max(first, second)
    return (start.toLong() shl 32) or end.toLong()
}
