package com.example.pcovviewer

data class Connection(
    val start: PcoParser.PcoPoint,
    val end: PcoParser.PcoPoint,
    val style: ConnectionStyle
)

object ConnectionBuilder {

    fun build(points: List<PcoParser.PcoPoint>): List<Connection> {
        if (points.isEmpty()) return emptyList()

        val pointsByNumber = points.associateBy { it.number }
        val sortedPoints = points.sortedBy { it.number }
        val result = mutableListOf<Connection>()
        val deduplicationSet = mutableSetOf<Long>()

        fun addConnection(first: PcoParser.PcoPoint, second: PcoParser.PcoPoint, style: ConnectionStyle) {
            if (first === second) return
            val key = orderedConnectionKey(first.number, second.number)
            if (deduplicationSet.add(key)) {
                result += Connection(first = first, end = second, style = style)
            }
        }

        sortedPoints.forEach { current ->
            val info = current.codeInfo

            info.connectionTargets.forEach { targetNumber ->
                val previousNumber = current.number - 1
                val shouldSkipPreviousConnection =
                    targetNumber == previousNumber && info.connectionTargets.size == 1

                if (shouldSkipPreviousConnection) {
                    return@forEach
                }

                val target = pointsByNumber[targetNumber]
                if (target != null) {
                    val style = CodeRules.connectionStyleForManualTargets(current, target)
                    addConnection(current, target, style)
                }
            }
        }

        return result
    }
}
