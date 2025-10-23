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

        val scaleX = width / spanX
        val scaleY = height / spanY
        val scale = min(scaleX, scaleY)

        val offsetX = (width - spanX * scale) / 2f
        val offsetY = (height - spanY * scale) / 2f

        val scaledPoints = visiblePoints.map { point ->
            val scaledX = offsetX + (point.x - minX) * scale
            val scaledY = offsetY + (maxY - point.y) * scale
            ScaledPoint(point, scaledX, scaledY)
        }

        val connections = buildConnections(scaledPoints)

        return Geometry(points = scaledPoints, connections = connections)
    }

    private fun buildConnections(points: List<ScaledPoint>): List<Pair<ScaledPoint, ScaledPoint>> {
        if (points.isEmpty()) return emptyList()

        val pointsByNumber = points.associateBy { it.point.number }
        val sortedPoints = points.sortedBy { it.point.number }
        val result = mutableListOf<Pair<ScaledPoint, ScaledPoint>>()
        val deduplicationSet = mutableSetOf<Long>()

        fun addConnection(first: ScaledPoint, second: ScaledPoint) {
            if (first === second) return
            val key = orderedConnectionKey(first.point.number, second.point.number)
            if (deduplicationSet.add(key)) {
                result += first to second
            }
        }

        sortedPoints.forEach { current ->
            val info = current.point.codeInfo

            info.connectionTargets.forEach { targetNumber ->
                val previousNumber = current.point.number - 1
                // Skip linking codes formatted as "CODE..POINT_NUMBER" to the immediately
                // preceding point in the numerical sequence.
                val shouldSkipPreviousConnection =
                    targetNumber == previousNumber &&
                        info.baseCode.isNotEmpty() &&
                        info.connectionTargets.size == 1

                if (shouldSkipPreviousConnection) {
                    return@forEach
                }

                val target = pointsByNumber[targetNumber]
                if (target != null) {
                    addConnection(current, target)
                }
            }
        }

        return result
    }
}
