package com.example.pcovviewer

data class PolylineChain(
    val points: List<PcoParser.PcoPoint>,
    val isClosed: Boolean
)

object PolylineBuilder {

    fun buildChains(
        points: List<PcoParser.PcoPoint>,
        connections: List<Connection>
    ): List<PolylineChain> {
        if (points.isEmpty() || connections.isEmpty()) {
            return emptyList()
        }

        val numberToPoint = points.associateBy { it.number }
        val adjacency = mutableMapOf<Int, MutableSet<Int>>()

        fun addEdge(first: Int, second: Int) {
            adjacency.getOrPut(first) { mutableSetOf() }.add(second)
            adjacency.getOrPut(second) { mutableSetOf() }.add(first)
        }

        connections.forEach { connection ->
            addEdge(connection.start.number, connection.end.number)
        }

        if (adjacency.isEmpty()) {
            return emptyList()
        }

        val visitedEdges = mutableSetOf<Long>()
        val result = mutableListOf<PolylineChain>()

        fun traverse(start: Int, neighbor: Int): PolylineChain? {
            val startPoint = numberToPoint[start] ?: return null
            val neighborPoint = numberToPoint[neighbor] ?: return null

            val edgeKey = orderedConnectionKey(start, neighbor)
            if (!visitedEdges.add(edgeKey)) {
                return null
            }

            val chain = mutableListOf(startPoint, neighborPoint)
            var previous = start
            var current = neighbor

            while (true) {
                val nextCandidates = adjacency[current]?.filter { it != previous }.orEmpty()
                if (nextCandidates.size != 1) {
                    break
                }

                val next = nextCandidates.first()
                val nextKey = orderedConnectionKey(current, next)
                if (!visitedEdges.add(nextKey)) {
                    break
                }

                val nextPoint = numberToPoint[next] ?: break
                chain += nextPoint
                previous = current
                current = next
            }

            return PolylineChain(points = chain, isClosed = false)
        }

        val startNodes = adjacency.keys.filter { (adjacency[it]?.size ?: 0) != 2 }.sorted()
        startNodes.forEach { start ->
            adjacency[start]?.sorted()?.forEach { neighbor ->
                traverse(start, neighbor)?.let(result::add)
            }
        }

        connections.forEach { connection ->
            val start = connection.start.number
            val end = connection.end.number
            val key = orderedConnectionKey(start, end)
            if (visitedEdges.contains(key)) {
                return@forEach
            }

            val startPoint = numberToPoint[start] ?: return@forEach
            val endPoint = numberToPoint[end] ?: return@forEach

            visitedEdges.add(key)
            val chain = mutableListOf(startPoint, endPoint)
            var previous = start
            var current = end
            val cycleStart = start
            var closed = false

            while (true) {
                if (current == cycleStart) {
                    closed = true
                    break
                }

                val nextCandidates = adjacency[current]?.filter { it != previous }.orEmpty()
                if (nextCandidates.isEmpty()) {
                    break
                }

                val next = nextCandidates.first()
                val nextKey = orderedConnectionKey(current, next)
                if (!visitedEdges.add(nextKey)) {
                    if (next == cycleStart) {
                        closed = true
                    }
                    break
                }

                val nextPoint = numberToPoint[next] ?: break
                chain += nextPoint
                previous = current
                current = next
            }

            if (closed && chain.size > 1 && chain.last().number == cycleStart) {
                chain.removeAt(chain.lastIndex)
            }

            if (chain.size >= 2) {
                result += PolylineChain(points = chain, isClosed = closed)
            }
        }

        return result
    }
}
