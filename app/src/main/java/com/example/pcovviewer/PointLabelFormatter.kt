package com.example.pcovviewer

object PointLabelFormatter {

    fun buildLabel(point: PcoParser.PcoPoint, connectedPointNumbers: List<Int>): String {
        val connections = connectedPointNumbers
            .distinct()
            .sorted()
            .joinToString(separator = ".")

        val codeLine = when {
            connections.isNotEmpty() && point.codeInfo.baseCode.isNotEmpty() ->
                "${point.codeInfo.baseCode}..$connections"
            connections.isNotEmpty() ->
                "..$connections"
            point.codeInfo.baseCode.isNotEmpty() ->
                point.codeInfo.baseCode
            point.code.isNotBlank() ->
                point.code.trim()
            else ->
                ""
        }

        return listOfNotNull(
            point.number.toString(),
            codeLine.takeIf { it.isNotEmpty() }
        ).joinToString(separator = "\n")
    }
}
