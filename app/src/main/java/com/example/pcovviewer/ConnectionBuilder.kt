package com.example.pcovviewer

object ConnectionBuilder {

    fun build(points: List<PcoParser.PcoPoint>): List<Pair<PcoParser.PcoPoint, PcoParser.PcoPoint>> {
        if (points.isEmpty()) return emptyList()

        val pointsByNumber = points.associateBy { it.number }
        val sortedPoints = points.sortedBy { it.number }
        val result = mutableListOf<Pair<PcoParser.PcoPoint, PcoParser.PcoPoint>>()
        val deduplicationSet = mutableSetOf<Long>()

        fun addConnection(first: PcoParser.PcoPoint, second: PcoParser.PcoPoint) {
            if (first === second) return
            val key = orderedConnectionKey(first.number, second.number)
            if (deduplicationSet.add(key)) {
                result += first to second
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
                    addConnection(current, target)
                }
            }
        }

        return result
    }
}
