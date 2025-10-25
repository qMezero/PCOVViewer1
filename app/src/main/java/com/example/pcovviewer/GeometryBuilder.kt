package com.example.pcovviewer

import java.util.LinkedHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
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

data class ArcParameters(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val startAngleDegrees: Float,
    val sweepAngleDegrees: Float
)

data class Connection(
    val start: ScaledPoint,
    val end: ScaledPoint,
    val style: ConnectionStyle,
    val arc: ArcParameters? = null
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

        if (points.isEmpty()) {
            return null
        }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

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

        val scaledPoints = points.map { point ->
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
                    val style = if (info.baseCode == "30") {
                        ConnectionStyle.DOTTED
                    } else {
                        ConnectionStyle.SOLID
                    }
                    addConnection(current, target, style)
                }
            }
        }

        val arcRuns = detectArcRuns(sortedNumbers, pointsByNumber)
        arcRuns.forEach { run ->
            val arcParameters = computeArcParameters(run) ?: return@forEach

            var style: ConnectionStyle? = null
            for (index in 0 until run.size - 1) {
                val start = run[index]
                val end = run[index + 1]
                val key = orderedConnectionKey(start.point.number, end.point.number)
                val existing = deduplicationMap[key] ?: return@forEach
                if (style == null) {
                    style = existing.style
                }
            }

            val resolvedStyle = style ?: return@forEach

            for (index in 0 until run.size - 1) {
                val start = run[index]
                val end = run[index + 1]
                val key = orderedConnectionKey(start.point.number, end.point.number)
                deduplicationMap.remove(key)
            }

            val arcKey = orderedConnectionKey(run.first().point.number, run.last().point.number)
            deduplicationMap[arcKey] = Connection(
                start = run.first(),
                end = run.last(),
                style = resolvedStyle,
                arc = arcParameters
            )
        }

        return deduplicationMap.values.toList()
    }

    private fun detectArcRuns(
        sortedNumbers: List<Int>,
        pointsByNumber: Map<Int, ScaledPoint>
    ): List<List<ScaledPoint>> {
        if (sortedNumbers.isEmpty()) return emptyList()

        val runs = mutableListOf<List<ScaledPoint>>()
        var currentRun: MutableList<ScaledPoint>? = null

        fun flushRun() {
            val run = currentRun
            if (run != null && run.size >= 3) {
                runs += run.toList()
            }
            currentRun = null
        }

        sortedNumbers.forEach { number ->
            val current = pointsByNumber[number] ?: return@forEach
            val previous = pointsByNumber[number - 1]
            val currentInfo = current.point.codeInfo
            val previousInfo = previous?.point?.codeInfo

            val shouldLink = previous != null &&
                currentInfo.connectsToPrevious &&
                !isDottedPreviousConnection(current.point) &&
                currentInfo.baseCode.isNotEmpty() &&
                previousInfo?.baseCode == currentInfo.baseCode

            if (shouldLink) {
                val run = currentRun ?: mutableListOf<ScaledPoint>().also { currentRun = it }
                if (run.isEmpty()) {
                    run += previous!!
                } else if (run.last() !== previous) {
                    run += previous!!
                }
                run += current
            } else {
                flushRun()
            }
        }

        flushRun()

        return runs
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

private data class Circle(val centerX: Double, val centerY: Double, val radius: Double)

private const val MIN_SWEEP_DEGREES = 5.0

private fun computeArcParameters(points: List<ScaledPoint>): ArcParameters? {
    if (points.size < 3) return null

    val first = points.first()
    val last = points.last()

    var circle: Circle? = null
    for (index in 1 until points.lastIndex) {
        circle = circleThrough(first, points[index], last)
        if (circle != null) {
            break
        }
    }

    val resolvedCircle = circle ?: return null

    val centerX = resolvedCircle.centerX
    val centerY = resolvedCircle.centerY
    val radius = resolvedCircle.radius

    if (!radius.isFinite() || radius <= 0.0) {
        return null
    }

    val angles = points.map { point ->
        atan2((point.y - centerY).toDouble(), (point.x - centerX).toDouble())
    }

    val unwrapped = unwrapAngles(angles)
    if (unwrapped.size < 2) {
        return null
    }

    val sweep = unwrapped.last() - unwrapped.first()
    val sweepDegrees = Math.toDegrees(sweep)
    if (abs(sweepDegrees) < MIN_SWEEP_DEGREES) {
        return null
    }

    val maxDeviation = points.maxOf { point ->
        val distance = hypot((point.x - centerX).toDouble(), (point.y - centerY).toDouble())
        abs(distance - radius)
    }

    val radiusTolerance = max(radius * 0.01, 1.5)
    if (maxDeviation > radiusTolerance) {
        return null
    }

    val startAngle = normalizeDegrees(Math.toDegrees(unwrapped.first()))

    return ArcParameters(
        centerX = centerX.toFloat(),
        centerY = centerY.toFloat(),
        radius = radius.toFloat(),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = sweepDegrees.toFloat()
    )
}

private fun circleThrough(first: ScaledPoint, middle: ScaledPoint, last: ScaledPoint): Circle? {
    val x1 = first.x.toDouble()
    val y1 = first.y.toDouble()
    val x2 = middle.x.toDouble()
    val y2 = middle.y.toDouble()
    val x3 = last.x.toDouble()
    val y3 = last.y.toDouble()

    val a1 = x1 - x2
    val b1 = y1 - y2
    val a2 = x1 - x3
    val b2 = y1 - y3

    val d1 = ((x1 * x1 + y1 * y1) - (x2 * x2 + y2 * y2)) / 2.0
    val d2 = ((x1 * x1 + y1 * y1) - (x3 * x3 + y3 * y3)) / 2.0

    val determinant = a1 * b2 - b1 * a2
    if (abs(determinant) < 1e-6) {
        return null
    }

    val centerX = (d1 * b2 - b1 * d2) / determinant
    val centerY = (a1 * d2 - d1 * a2) / determinant
    val radius = hypot(centerX - x1, centerY - y1)

    return Circle(centerX, centerY, radius)
}

private fun unwrapAngles(angles: List<Double>): List<Double> {
    if (angles.isEmpty()) return emptyList()

    val unwrapped = ArrayList<Double>(angles.size)
    var current = angles.first()
    unwrapped += current

    for (index in 1 until angles.size) {
        var angle = angles[index]
        var delta = angle - current

        while (delta <= -PI) {
            angle += 2.0 * PI
            delta = angle - current
        }

        while (delta > PI) {
            angle -= 2.0 * PI
            delta = angle - current
        }

        current = angle
        unwrapped += current
    }

    return unwrapped
}

private fun normalizeDegrees(angle: Double): Float {
    var normalized = angle % 360.0
    if (normalized < 0.0) {
        normalized += 360.0
    }
    return normalized.toFloat()
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
