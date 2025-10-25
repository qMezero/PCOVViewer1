package com.example.pcovviewer

import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a point positioned inside a drawing area after scaling.
 */
data class ScaledPoint(
    val point: PcoParser.PcoPoint,
    val x: Float,
    val y: Float
)

enum class ConnectionStyle {
    SOLID,
    DOTTED
}

data class Connection(
    val start: ScaledPoint,
    val end: ScaledPoint,
    val style: ConnectionStyle
)

/**
 * Aggregates geometry needed for both on-screen preview and PDF export.
 */
data class Geometry(
    val points: List<ScaledPoint>,
    val connections: List<Connection>,
    val scale: Float
)

object GeometryBuilder {

    private const val EDGE_PADDING_FRACTION = 0.05f
    private const val MIN_EDGE_PADDING = 24f

    fun build(points: List<PcoParser.PcoPoint>, width: Float, height: Float): Geometry? {
        if (width <= 0f || height <= 0f) {
            return null
        }

        val visiblePoints = points.filterNot { CodeRules.isHidden(it) }
        if (visiblePoints.isEmpty()) {
            return null
        }

        val minX = visiblePoints.minOf { it.x }
        val maxX = visiblePoints.maxOf { it.x }
        val minY = visiblePoints.minOf { it.y }
        val maxY = visiblePoints.maxOf { it.y }

        val spanX = max(maxX - minX, 1e-6f)
        val spanY = max(maxY - minY, 1e-6f)

        val rotatedSpanX = spanY
        val rotatedSpanY = spanX

        val minDimension = min(width, height)
        val padding = max(MIN_EDGE_PADDING, minDimension * EDGE_PADDING_FRACTION)
        val availableWidth = max(width - padding * 2f, 1f)
        val availableHeight = max(height - padding * 2f, 1f)

        val scaleX = availableWidth / rotatedSpanX
        val scaleY = availableHeight / rotatedSpanY
        val scale = min(scaleX, scaleY)

        val offsetX = padding + (availableWidth - rotatedSpanX * scale) / 2f
        val offsetY = padding + (availableHeight - rotatedSpanY * scale) / 2f

        val scaledPoints = visiblePoints.map { point ->
            val rotatedX = offsetX + (point.y - minY) * scale
            val flippedY = offsetY + (maxX - point.x) * scale
            ScaledPoint(point, rotatedX, flippedY)
        }

        val connections = buildConnections(scaledPoints)

        return Geometry(points = scaledPoints, connections = connections, scale = scale)
    }

    private fun buildConnections(points: List<ScaledPoint>): List<Connection> {
        if (points.isEmpty()) return emptyList()

        val pointsByNumber = points.associateBy { it.point.number }
        val sortedNumbers = pointsByNumber.keys.sorted()
        val deduplicationMap = LinkedHashMap<Long, Connection>()

        fun addConnection(from: ScaledPoint, to: ScaledPoint, style: ConnectionStyle) {
            if (from === to) return
            if (shouldSkipConnection(from, to)) return

            val key = orderedConnectionKey(from.point.number, to.point.number)
            val existing = deduplicationMap[key]
            if (existing == null || (existing.style == ConnectionStyle.SOLID && style == ConnectionStyle.DOTTED)) {
                deduplicationMap[key] = Connection(start = from, end = to, style = style)
            }
        }

        sortedNumbers.forEach { number ->
            val current = pointsByNumber[number] ?: return@forEach

            val previous = pointsByNumber[number - 1]
            val info = current.point.codeInfo
            if (previous != null && info.connectsToPrevious && info.connectionTargets.isEmpty()) {
                val style = if (isDottedPreviousConnection(current.point)) {
                    ConnectionStyle.DOTTED
                } else {
                    ConnectionStyle.SOLID
                }
                addConnection(previous, current, style)
            }

            info.connectionTargets.forEach { targetNumber ->
                val target = pointsByNumber[targetNumber]
                if (target != null) {
                    addConnection(current, target, ConnectionStyle.SOLID)
                }
            }
        }

        return deduplicationMap.values.toList()
    }

    private fun shouldSkipConnection(first: ScaledPoint, second: ScaledPoint): Boolean {
        val (lower, higher) = if (first.point.number <= second.point.number) {
            first to second
        } else {
            second to first
        }

        if (higher.point.number != lower.point.number + 1) {
            return false
        }

        val info = higher.point.codeInfo
        if (!info.connectsToPrevious || info.baseCode.isNotEmpty()) {
            return false
        }

        val targets = info.connectionTargets
        if (targets.size != 1 || targets.first() != lower.point.number) {
            return false
        }

        val rawCode = higher.point.code.trim().trimEnd { it == '.' }
        if (!rawCode.startsWith("..")) {
            return false
        }

        val suffix = rawCode.substring(2).trim()
        if (suffix == lower.point.number.toString()) {
            return true
        }

        val lowerPointNames = lower.point.possibleNames()
        if (lowerPointNames.isEmpty()) {
            return false
        }

        return lowerPointNames.any { candidate -> candidate.equals(suffix, ignoreCase = true) }
    }
}

private fun isDottedPreviousConnection(point: PcoParser.PcoPoint): Boolean {
    val trimmedCode = point.code.trim()
    return point.codeInfo.baseCode == "30" && trimmedCode.equals("30..", ignoreCase = false)
}

private val nonNameAttributeKeys = setOf("4", "5", "37", "38", "39")

private fun PcoParser.PcoPoint.possibleNames(): List<String> {
    if (attributes.isEmpty()) return emptyList()

    return attributes
        .asSequence()
        .filter { (key, _) -> key !in nonNameAttributeKeys }
        .map { (_, value) -> value.trim() }
        .filter { it.isNotEmpty() && it.any { ch -> ch.isLetter() } }
        .toList()
}
