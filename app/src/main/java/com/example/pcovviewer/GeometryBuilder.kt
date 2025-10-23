package com.example.pcovviewer

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

/**
 * Aggregates geometry needed for both on-screen preview and PDF export.
 */
data class Geometry(
    val points: List<ScaledPoint>,
    val connections: List<Pair<ScaledPoint, ScaledPoint>>
)

object GeometryBuilder {

    fun build(
        points: List<PcoParser.PcoPoint>,
        width: Float,
        height: Float,
        style: SchemeStyle
    ): Geometry? {
        if (width <= 0f || height <= 0f) {
            return null
        }

        val visiblePoints = points.filterNot { CodeRules.isHidden(it) }
        if (visiblePoints.isEmpty()) {
            return null
        }

        val padding = computePadding(visiblePoints, style)
        val usableWidth = width - padding.horizontal
        val usableHeight = height - padding.vertical
        if (usableWidth <= 0f || usableHeight <= 0f) {
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

        val scaleX = usableWidth / rotatedSpanX
        val scaleY = usableHeight / rotatedSpanY
        val scale = min(scaleX, scaleY)

        val offsetX = padding.left + (usableWidth - rotatedSpanX * scale) / 2f
        val offsetY = padding.top + (usableHeight - rotatedSpanY * scale) / 2f

        val scaledPoints = visiblePoints.map { point ->
            val rotatedX = offsetX + (point.y - minY) * scale
            val rotatedY = offsetY + (point.x - minX) * scale
            ScaledPoint(point, rotatedX, rotatedY)
        }

        val connections = buildConnections(scaledPoints)

        return Geometry(points = scaledPoints, connections = connections)
    }

    private fun buildConnections(points: List<ScaledPoint>): List<Pair<ScaledPoint, ScaledPoint>> {
        if (points.isEmpty()) return emptyList()

        val pointsByNumber = points.associateBy { it.point.number }
        val previousByCode = mutableMapOf<String, ScaledPoint>()
        val result = mutableListOf<Pair<ScaledPoint, ScaledPoint>>()
        val deduplicationSet = mutableSetOf<Long>()

        fun addConnection(from: ScaledPoint, to: ScaledPoint) {
            if (from === to) return
            val key = orderedConnectionKey(from.point.number, to.point.number)
            if (deduplicationSet.add(key)) {
                result += from to to
            }
        }

        points.forEach { scaledPoint ->
            val info = scaledPoint.point.codeInfo

            if (info.connectsToPrevious) {
                val previous = previousByCode[info.baseCode]
                if (previous != null) {
                    addConnection(previous, scaledPoint)
                }
            }

            info.connectionTargets.forEach { targetNumber ->
                val target = pointsByNumber[targetNumber]
                if (target != null) {
                    addConnection(scaledPoint, target)
                }
            }

            previousByCode[info.baseCode] = scaledPoint
        }

        return result
    }

    private fun computePadding(points: List<PcoParser.PcoPoint>, style: SchemeStyle): Padding {
        if (points.isEmpty()) {
            return Padding.zero()
        }

        val textPaint = style.createBaseTextPaint()
        val fontMetrics = textPaint.fontMetrics

        var minOffsetX = -style.basePointRadius
        var maxOffsetX = style.basePointRadius
        var minOffsetY = -style.basePointRadius
        var maxOffsetY = style.basePointRadius

        points.forEach { point ->
            val lines = style.labelLines(point)
            val maxLineWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f

            val startX = style.baseLabelOffsetX
            val endX = style.baseLabelOffsetX + maxLineWidth

            minOffsetX = min(minOffsetX, min(startX, endX))
            maxOffsetX = max(maxOffsetX, max(startX, endX))

            if (lines.isNotEmpty()) {
                lines.indices.forEach { index ->
                    val baselineOffset = -style.baseLabelOffsetY + index * (style.baseTextSize + style.baseLineSpacing)
                    val top = baselineOffset + fontMetrics.top
                    val bottom = baselineOffset + fontMetrics.bottom
                    minOffsetY = min(minOffsetY, top)
                    maxOffsetY = max(maxOffsetY, bottom)
                }
            }
        }

        val left = style.outerPadding + -minOffsetX
        val right = style.outerPadding + maxOffsetX
        val top = style.outerPadding + -minOffsetY
        val bottom = style.outerPadding + maxOffsetY

        return Padding(left, top, right, bottom)
    }
}

private data class Padding(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val horizontal: Float get() = left + right
    val vertical: Float get() = top + bottom

    companion object {
        fun zero() = Padding(0f, 0f, 0f, 0f)
    }
}
