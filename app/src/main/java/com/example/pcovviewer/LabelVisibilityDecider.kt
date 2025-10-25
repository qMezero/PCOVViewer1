package com.example.pcovviewer

import kotlin.math.hypot

object LabelVisibilityDecider {

    fun determineVisibleLabelNumbers(
        points: List<ScaledPoint>,
        clusterThreshold: Float
    ): Set<Int> {
        if (points.isEmpty()) {
            return emptySet()
        }

        if (clusterThreshold <= 0f || clusterThreshold.isNaN()) {
            return points.mapTo(mutableSetOf()) { it.point.number }
        }

        val sortedPoints = points.sortedBy { it.point.number }
        val visible = sortedPoints.mapTo(mutableSetOf()) { it.point.number }

        var clusterStartIndex = -1

        for (index in 1 until sortedPoints.size) {
            val previous = sortedPoints[index - 1]
            val current = sortedPoints[index]
            val distance = hypot(
                (current.x - previous.x).toDouble(),
                (current.y - previous.y).toDouble()
            ).toFloat()

            if (distance < clusterThreshold) {
                if (clusterStartIndex == -1) {
                    clusterStartIndex = index - 1
                }
            } else if (clusterStartIndex != -1) {
                val clusterEndIndex = index - 1
                for (suppressedIndex in (clusterStartIndex + 1) until clusterEndIndex) {
                    visible.remove(sortedPoints[suppressedIndex].point.number)
                }
                clusterStartIndex = -1
            }
        }

        if (clusterStartIndex != -1) {
            for (suppressedIndex in (clusterStartIndex + 1) until sortedPoints.lastIndex) {
                visible.remove(sortedPoints[suppressedIndex].point.number)
            }
        }

        return visible
    }
}
